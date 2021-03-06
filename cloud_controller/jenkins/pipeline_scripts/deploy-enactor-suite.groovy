#!groovy

/*Capture all the input parameters */

import groovy.json.JsonOutput

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== Jenkins Environment Variables =================== */
def svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
def svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External Parameters =================== */
String svnCredentialID           = params.CUSTOMER_CREDENTIAL_ID
String customer                  = params.CUSTOMER_NAME
String environment               = params.ENV_NAME
String enactorDockerImageTag     = params.STANDARD_DOCKER_VERSION
String mysqlDockerImageTag       = params.STANDARD_MYSQL_VERSION
String dockerRegistryCredentials = params.DOCKER_REGISTRY_CREDENTIALS
String publicIpAdress            = params.PUBLIC_IP_ADDRESS
String containerRegistry         = params.CONTAINER_REGISTRY
def forceRedeployment          = (params.FORCE_REDEPLOYMENT == null) ? true : params.FORCE_REDEPLOYMENT
String os

def selectedServices
def volumeList
def isNfsVolumes = false
def windowsNetworkDriveMountTarget = 'C:\\enactor\\shared\\scripts\\mount\\mount' 
def windowsNetworkDriveLetterPlaceholder = 'NETWORK_DRIVE_LETTER'

/*===================== SVN Directories =================== */
String playbooksDir = "ansible"
String stacksDir = "deployment-architectures"
String customerEnvDir = "customer-env"

/*===================== Directories Inside Environment =================== */
String infrastructure = 'infrastructure'
String deployment = 'deployment'

/*===================== Internal Parameters =================== */
String workspacePath

/*===================== Jenkins Pipeline =================== */
node('master') {
    svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
}

node("${env.SELF_SERVICE_NODE}") {
	try {
		stage('Init & Checkout') {
			cleanWs()
			workspacePath = pwd()
            parallel(
                checkout_playbook: {
					svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${playbooksDir}/", "${playbooksDir}")
				},
				checkout_stack: {
                    svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${stacksDir}/", "${stacksDir}")            							
				},
                checkout_configs: {
                    svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}/${environment}", "${customerEnvDir}")            							
				}
			)
            os = pipelineHelper.getEnv("${customerEnvDir}", 'os')
		}

        stage('Prepare Service Stack Directory Ready for the Build') {
            // Remove the general envs directory from the deployment-architecture.
            dir("${stacksDir}/${os}/stacks/envs") {
                deleteDir()
            }
            // Copy the envs directory from the customer folder.
            sh "cp -a ${customerEnvDir}/${deployment}/envs ${stacksDir}/${os}/stacks/envs"

            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def enactorSuiteParams = deploymentParams['enactor-suite-params']

            isNfsVolumes = enactorSuiteParams['IS_NFS_VOLUMES']

            selectedServices = enactorSuiteParams['service-list']
            def serviceFeatureMap = enactorSuiteParams['service-feature-map']
            dir("${stacksDir}/${os}/stacks") {

                def serviceConfigMap = readJSON(file: 'service-config-map.json')
                volumeList = []

                selectedServices.each{ service ->
                    def stackFile = readYaml(file: service)
                    def serviceName = serviceConfigMap[service]['service_name']

                    def portPublishingMode = serviceFeatureMap[service]['PORT_PUBLISHING_MODE']
                    def replicas = serviceFeatureMap[service]['REPLICAS']
                    def constraints = serviceFeatureMap[service]['CONSTRAINTS']
                    def imagePath = serviceFeatureMap[service]['IMAGE_PATH']
                    def imageTag = serviceFeatureMap[service]['IMAGE_TAG']

                    // Assign customer environment specific feature values to service stack.
                    for(def i=0; i<stackFile['services'][serviceName]['ports'].size(); i++){
                        stackFile['services'][serviceName]['ports'][i]['mode'] = portPublishingMode
                    }
                    stackFile['services'][serviceName]['deploy']['endpoint_mode'] = (portPublishingMode == 'host') ? 'dnsrr' : 'vip'
                    // File Beats (file-beat-stack.yml) and Metric Beats (metric-beat-stack.yml) should run in the 'global' mode.
                    // Services running in the 'global' mode cannot have 'replicas' parameter.
                    if ( service != 'file-beat-stack.yml' && service != 'metric-beat-stack.yml' ) {
                        stackFile['services'][serviceName]['deploy']['replicas'] = replicas                        
                    }
                    stackFile['services'][serviceName]['deploy']['placement']['constraints'] = constraints

                    def defaultImageName = stackFile['services'][serviceName]['image']
                    imageTag = imageTag?.trim() ? imageTag.trim() : defaultImageName.split(':')[1]
                    // Update image name
                    if (imagePath?.trim()) {
                        imagePath = imagePath.trim()
                        imagePath = imagePath.endsWith('/') ? imagePath.substring(0, imagePath.length() - 1) : imagePath
                        imagePath = imagePath.startsWith('/') ? imagePath.substring(1, imagePath.length()) : imagePath
                    } else {
                        // No change in imagePath. But check if registry has changed
                        int index = defaultImageName.indexOf('/')
                        index = index < 0 ? 0 : index
                        imagePath = defaultImageName.split(':')[0].substring(index + 1)
                    }
                    echo "Setting image for service: ${serviceName}, ${containerRegistry}/${imagePath}:${imageTag}"
                    stackFile['services'][serviceName]['image'] = "${containerRegistry}/${imagePath}:${imageTag}"

                    // Remove File before writing it. writeYaml does not support over-write.
                    sh "rm ${service}"
                    // Write the modified yaml stack files.
                    writeYaml(file: service, data: stackFile, charset: 'UTF-8')

                    // Populate the required volume list for the services.
                    serviceConfigMap[service]['volumes'].each{ volume ->
                        volumeList << volume
                    }
                }

                // Remove duplicated volumes.
                volumeList = volumeList as Set
                volumeList = volumeList as List
            }
        }

        stage('Create Docker Volumes or Network Drives') {
            def hostsFile = "${workspacePath}/${customerEnvDir}/${infrastructure}/infrastructure-details.json";
            def sevices_to_deploy = '{"services_to_deploy":' + JsonOutput.toJson(selectedServices) + '}'
            def volume_list = '{"volume_list":' + JsonOutput.toJson(volumeList) + '}'
            def resourcesPath = "${workspacePath}/${stacksDir}/${os}/stacks/resources"
            
            // Create docker volumes or network drive mount scripts.
            dir ('ansible/enactor-automation') {
                // Create network volumes/drives.
                if (isNfsVolumes) {
                    if (os == 'linux'){
                        sh "ansible-playbook -i ${hostsFile} linux-create-volumes.yml \
                        -e is_nfs=true \
                        --extra-vars '${volume_list}'"
                    } else {
                        // windows platform
                        sh "ansible-playbook -i ${hostsFile} windows-create-network-drives.yml \
                        -e mount_script_location=${resourcesPath} \
                        --extra-vars '${volume_list}'"
                    }
                } else {
                    // Create local volumes.
                    if (os == 'linux'){
                        sh "ansible-playbook -i ${hostsFile} linux-create-volumes.yml \
                        -e is_nfs=false \
                        --extra-vars '${volume_list}'"
                    } else {
                        // windows platform
                        sh "ansible-playbook -i ${hostsFile} windows-create-volumes.yml \
                        --extra-vars '${volume_list}'"
                    }
                }
            }

            // Add mount scripts as secrets to the corresponding scripts. Relevent only for windows clusters.
            dir("${stacksDir}/${os}/stacks") {
                def serviceConfigMap = readJSON(file: 'service-config-map.json')

                if (os == 'windows' && isNfsVolumes) {
                    // If there are more than one network drive to mount, 
                    // drive name get assigned according to the volumes order in service-config-map.json.
                    // Assumption: there will be maximum of 5 network drives per service.
                    def driveLetterSequence = ['Z', 'Y', 'X', 'W', 'U'] 

                    selectedServices.each{ service -> 
                        def serviceName = serviceConfigMap[service]['service_name']

                        serviceConfigMap[service]['volumes'].eachWithIndex{ volume, index ->
                            def stackFile = readYaml(file: service)     
                            def secretsDeclaration = stackFile['secrets']
                            def secretsMount = stackFile['services'][serviceName]['secrets']
                            def tmplMountScript = "${resourcesPath}/${volume}"

                            if (fileExists(file: tmplMountScript)) {
                                def servicePrefix = service.minus('.yml')
                                def mountScriptName = "${servicePrefix}_${volume}"
                                def scriptDirPath = "${resourcesPath}/${servicePrefix}"
                                
                                def scriptDir = new File( scriptDirPath )
                                if( !scriptDir.exists() ) { 
                                    sh "mkdir -p ${scriptDirPath}"
                                 }
                                // Substitute the NETWOR_DRIVE_LETTER to the mount script.
                                writeFile(
                                    file: "${scriptDirPath}/${mountScriptName}",
                                    text: readFile(file: tmplMountScript).replaceAll(windowsNetworkDriveLetterPlaceholder, driveLetterSequence[index])
                                )
                                
                                // Insert secret declaration to the stack file.
                                if (secretsDeclaration) {
                                    // 'secrets' declaration map already exists, append to the map.
                                    secretsDeclaration << ["${mountScriptName}": ['file': "./resources/${servicePrefix}/${mountScriptName}"]]
                                } else {
                                    // 'secrets' declaration map doenot exist, create a map and add.
                                    def secrets_decl = ["${mountScriptName}" : ['file': "./resources/${servicePrefix}/${mountScriptName}"]]
                                    stackFile << ['secrets': secrets_decl]
                                }

                                // Insert secret mount to the stack file.
                                if (secretsMount) {
                                    // 'secrets' mount list already exists, append to the map.
                                    secretsMount << ['source': "${mountScriptName}", 'target': "${windowsNetworkDriveMountTarget}${driveLetterSequence[index]}.ps1"]
                                } else {
                                    // 'secrets' mount list does not exist, create a map and add.
                                    stackFile['services'][serviceName] << ['secrets': [['source': "${mountScriptName}", 'target': "${windowsNetworkDriveMountTarget}${driveLetterSequence[index]}.ps1"]]]
                                }
                            }

                            // Remove File before writing it. writeYaml does not support over-write.
                            sh "rm ${service}"
                            // Write the modified yaml stack files.
                            writeYaml(file: service, data: stackFile, charset: 'UTF-8')
                        }
                    }
                }
            }
        }

        stage('Build') {
            dir("${stacksDir}/${os}") {
                pipelineHelper. execMaven(
                    "clean install -DSTANDARD_DOCKER_VERSION=${enactorDockerImageTag} \
                    -DSTANDARD_MYSQL_VERSION=${mysqlDockerImageTag} \
                    -DPUBLIC_IP=${publicIpAdress}"
                )
            }
        }

        stage('Invoke Deploy Suite Playbook') {
			def hostsFile = "${workspacePath}/${customerEnvDir}/${infrastructure}/infrastructure-details.json"
            def deploy_suite_zip_location = "${workspacePath}/${stacksDir}/${os}/target/${os}-swarm-stack-bundle.zip"
            def serviceConfigMap = readJSON(file: "${stacksDir}/${os}/stacks/service-config-map.json")

            // Order the selected services according to the priority.
            def orderedSelectedServices = []
            selectedServices.each{ service ->
                def serviceStack = [:]
                serviceStack['name'] = service
                serviceStack['deployment_priority'] = serviceConfigMap[service]['deployment_priority'] ?: 1
                serviceStack['wait_end_point'] = serviceConfigMap[service]['wait_end_point'] ?: ""
                serviceStack['wait_timeout'] = serviceConfigMap[service]['wait_timeout'] ?: -1
                orderedSelectedServices << serviceStack
            }
            // Implementing bubble-sort to sort the selected services list according to the 'deployment_priority'
            // This can be easily done with Groovy using a sort closure (i.e. list.sort{a,b -> a.value > b.value}).
            // However, Jenkins support for that is still pending. Refer to https://issues.jenkins-ci.org/browse/JENKINS-44924
            // Once this is supported, remove the following code segment and move to the more elegant method.
            def listSize = orderedSelectedServices.size()
            def temp, i, j
            for (i = 0; i < listSize; i++){
                for (j = 1; j < (listSize - i); j++){
                    if (orderedSelectedServices[j-1]['deployment_priority'] 
                            > orderedSelectedServices[j]['deployment_priority']) {
                        temp = orderedSelectedServices[j-1]
                        orderedSelectedServices[j-1] = orderedSelectedServices[j]
                        orderedSelectedServices[j] = temp
                    }
                }
            }
            def sevices_to_deploy = '{"services_to_deploy":' + JsonOutput.toJson(orderedSelectedServices) + '}'
            def volume_list = '{"volume_list":' + JsonOutput.toJson(volumeList) + '}'

            dir ('ansible/enactor-automation') {
                withCredentials([usernamePassword(credentialsId: dockerRegistryCredentials, passwordVariable: 'dockerRegistryPassword', usernameVariable: 'dockerRegistryUser')]) {
                    // Run deploy services playbook.
                    sh "ansible-playbook -i ${hostsFile} $os-deploy-suite.yml \
                    -e deploy_suite_zip_location=${deploy_suite_zip_location} \
                    -e remove_stack_before_deploy=${forceRedeployment ? 'true' : 'false'}\
                    -e docker_registry=${containerRegistry} \
                    -e docker_registry_username=${dockerRegistryUser} \
                    -e docker_registry_password=${dockerRegistryPassword} \
                    --extra-vars '${sevices_to_deploy}'"
                }
            }
        }
	} finally {
		cleanWs()
	}
}