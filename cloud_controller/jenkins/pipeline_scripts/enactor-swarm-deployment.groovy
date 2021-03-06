#!groovy
/* Capture all inputs required for all stages of the deployment and invoke downstream jobs sequentially */

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper

/*===================== Jenkins Environment Variables =================== */
String svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
String svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== SVN Directories =================== */
String playbooksDir = "ansible"
String stacksDir = "deployment-architectures"
String defaultInputsDir = "default_inputs"
String customersDir = "customers"
String infrastructureRootDir = "terraform"
String defaultInfrastructureInputsPath = "cloud_controller/jenkins/default_input_parameters/infrastructure_pipeline"
String leaderIP

/*===================== Directories Inside Environment =================== */
String infrastructure = 'infrastructure'
String deployment = 'deployment'

/*===================== Job Parameters =================== */
String svnCredentialID = params.CUSTOMER_CREDENTIAL_ID

boolean shouldCreateCustomer = params.NEW_CUSTOMER
boolean shouldCreateEnvironment = params.NEW_ENVIRONMENT
boolean shouldCreateInfra = params.CONFIGURE_INFRASTRUCTURE
boolean shouldConfigureNodes = params.CONFIGURE_NODES
boolean shouldSetupSwarm = params.SETUP_SWARM
boolean shouldDeployEnactorSuite = params.DEPLOY_ENACTOR_SUITE
String customerProductsPath = params.CUSTOMER_PRODUCTS_PATH
String customerEnvDir
String cloudProvider
String os

/*===================== Internal Parameters =================== */
String workspacePath
String customerName
String enviromentName
String enviromentPath
String customerPath


def servicePorts = [:]
List regionsList = []

/*===================== Load helper libraries =================== */
node('master') {
    svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
}

node("${env.SELF_SERVICE_NODE}") {
    try {
        /* Clean Jenkins workspace and initialize variables */
        stage('Init') {
            cleanWs()
            workspacePath = pwd()
            servicePorts = readJSON(text: params.SERVICE_PORTS)
        }

        /* Prompt user to enter customer name and environment name */
        stage('Capture environment details'){
            List environmentDetailsParams = []

            if(shouldCreateCustomer){
                if(!customerProductsPath){
                    environmentDetailsParams << [
                        $class: 'StringParameterDefinition',
                        name: 'CUSTOMER_NAME',
                        defaultValue: 'enactor',
                        description: 'Specify a non-existing customer name in the SVN repository path. Should only contain (a-z,A-Z,0-9)',
                        trim: true
                    ]
                }else if(customerProductsPath){
                    def customerDropdownOptions = svnHelper.getSvnFolderList(svnCredentialID, customerProductsPath, "/").join("\n")
                    environmentDetailsParams << [
                        $class: 'ChoiceParameterDefinition',
                        choices: customerDropdownOptions, 
                        description: 'Select an existing customer name', 
                        name: 'CUSTOMER_NAME'
                    ]
                }

                environmentDetailsParams << [
                    $class: 'StringParameterDefinition',
                    name: 'ENV_NAME',
                    defaultValue: 'test',
                    description: 'Specify a non-existing environment name in the selected customer SVN repository path. Should only contain (a-z,A-Z,0-9)',
                    trim: true
                ]
            }else{
                def customerDropdownOptions = svnHelper.getSvnFolderList(svnCredentialID, svnCustomersRepo, "/").join("\n")
                environmentDetailsParams << [
                    $class: 'ChoiceParameterDefinition',
                    choices: customerDropdownOptions, 
                    description: 'Select an existing customer name', 
                    name: 'CUSTOMER_NAME'
                ]
                if(shouldCreateEnvironment){
                    environmentDetailsParams << [
                        $class: 'StringParameterDefinition',
                        name: 'ENV_NAME',
                        defaultValue: 'test',
                        description: 'Specify a non-existing environment name in the selected customer SVN repository path. Should only contain (a-z,A-Z,0-9)',
                        trim: true
                    ]
                }
            }

            def newEnvironmentDetailsParams = input(message: 'Provide Environment Details', parameters: environmentDetailsParams)
            if(newEnvironmentDetailsParams instanceof String){
                customerName = newEnvironmentDetailsParams
            }else{
                customerName = newEnvironmentDetailsParams['CUSTOMER_NAME']
                enviromentName = newEnvironmentDetailsParams['ENV_NAME']
            }

            customerPath = customerName + "/" + params.CUSTOMER_PATH_POSTFIX

            if(!shouldCreateCustomer && !shouldCreateEnvironment){
                def environmentDropdownOptions = svnHelper.getSvnFolderList(svnCredentialID, svnCustomersRepo, "/${customerPath}").join("\n")
                environmentDetailsParams = [[
                    $class: 'ChoiceParameterDefinition',
                    choices: environmentDropdownOptions, 
                    description: 'Select an existing environment name.', 
                    name: 'ENV_NAME'
                ]]
                enviromentName = input(message: 'Provide Environment Details', parameters: environmentDetailsParams)
            }
            
            println("============Enactor application suite will be deployed in=================")
            println("Customer: ${customerName}")
            println("Environment: ${enviromentName}")
            println("===========================================================================")

            enviromentPath = "${customerPath}/${enviromentName}"
            customerEnvDir = "${customersDir}/${enviromentPath}"

            timeout(time: 60, unit: "SECONDS") {
                input message: "Do you want to proceed deployment for customer path: ${customerPath} and environment: ${enviromentName}?", ok: 'Yes'
            }
            
        }
        /* 
         * Checkout all required svn modules
        */
        stage('Checkout') {
            if(svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${enviromentPath}")){ // if env exists in svn
                println("================================= env exists")
                svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${enviromentPath}", "${customerEnvDir}")
            }else{

                parallel(
                    env_configs_checkout: {
                        echo "checkout env configs"
                        if(svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customerPath}")){ // if customer and path exists
                            println("================================= customer and path exists")
                            svnHelper.checkout(svnCredentialID, svnCustomersRepo, customerPath, "${customersDir}/${customerPath}", "immediates")
                        }else{
                            println("================================= new customer")
                            svnHelper.checkout(svnCredentialID, svnCustomersRepo, "", "${customersDir}", "immediates")
                        }
                    },
                    infra_checkout: {
                        echo "checkout infrastructure scripts"
                        svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${infrastructureRootDir}/", "${infrastructureRootDir}")
                    }
                )
                


                def environementTypeData = input(
                message: 'Operating system type of the cluster nodes.',
                parameters: [
                    [
                        $class: 'ChoiceParameterDefinition',
                        choices: 'linux\nwindows', 
                        description: 'Select operating system type of the cluster nodes for this environement.', 
                        name: 'OS_TYPE'
                    ],
                    [
                        $class: 'ChoiceParameterDefinition',
                        choices: 'aws\nazure\non-prem', 
                        description: 'Select infrastructure provider.', 
                        name: 'PROVIDER'
                    ]
                ])
                cloudProvider = environementTypeData['PROVIDER']
                os = environementTypeData['OS_TYPE']

                sh "mkdir -p ${customerEnvDir}/${infrastructure}"
                def sourcePath = "${workspacePath}/${infrastructureRootDir}"
                def destinationPath = "${customerEnvDir}/${infrastructure}"

                if(cloudProvider == "aws"){
                    sh "cp -a ${sourcePath}/default_scripts/aws/. ${destinationPath}"
                    sh "cp -a ${sourcePath}/modules/aws/. ${destinationPath}/modules"
                    sh "cp -a ${sourcePath}/templates ${destinationPath}"
                }else if(cloudProvider == "azure"){
                    sh "cp -a ${sourcePath}/default_scripts/azure/. ${destinationPath}"
                    sh "cp -a ${sourcePath}/modules/azure/. ${destinationPath}/modules"
                    sh "cp -a ${sourcePath}/templates ${destinationPath}"
                }

                def deploymentParams = pipelineHelper.createDefaultDeploymentParamsJson("${customerEnvDir}/deployment-params.json")
                def environmentParams = deploymentParams['environment-params']
                def svnRevision = svnHelper.execSVN("info --show-item revision ${infrastructureRootDir}/", svnCredentialID)
                environmentParams['os'] =  os
                environmentParams['provider'] = cloudProvider
                environmentParams['created_with_release'] = svnSelfServiceAppReleaseRepo + "/?p=" + svnRevision.trim()
                writeJSON(file: "${customerEnvDir}/deployment-params.json", json: deploymentParams)
            }

            parallel(
				checkout_stack: {
                    svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${stacksDir}/", "${stacksDir}")            							
				},
                checkout_default_inputs: {
                    svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${defaultInfrastructureInputsPath}/", "${defaultInputsDir}")           							
				}
			)
            
            os = pipelineHelper.getEnv("${customerEnvDir}", 'os')
            cloudProvider = pipelineHelper.getEnv("${customerEnvDir}", 'provider')
        }
        /* 
         * Prompt user to enter infrastructure create/update specifications
        */
        pipelineHelper.stage('Capture Parameters to Create or Update Infrastructure', shouldCreateInfra) {
            String defaultRegion
            String regionsParamName
            String regionsListString
            String infrastructureSpec
            String infraDefaultJsonInputPath
            def infrastructureInputParams = [[:]]

            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def infrastructureParams = deploymentParams['infrastructure-params']
            
            // Based on the infrastructure provider type obtain set of inputs for infrastructure creation.
            if(cloudProvider == "on-prem"){
                String infrastructureDetails
                try{
                    infrastructureDetails = readFile(file: "${workspacePath}/${customerEnvDir}/${infrastructure}/infrastructure-details.json")
                }catch(Exception ex){
                    if(os == "linux"){
                        infrastructureDetails = readFile(file: "${defaultInputsDir}/existing_linux.json")
                    }else if(os == "windows"){
                        infrastructureDetails = readFile(file: "${defaultInputsDir}/existing_windows.json")
                    }
                }
                infrastructureInputParams = [
                        [ 
                            $class: 'TextParameterDefinition',
                            name: 'INFRASTRUCTURE_CREDENTIALS',
                            defaultValue: infrastructureDetails,
                            description: 'Your existing infrastructure credentials.',
                            trim: true
                        ]
                    ]
            }else{ // Azure or AWS
                if(cloudProvider == "aws"){
                    regionsParamName = "AWS_REGIONS"
                    infraDefaultJsonInputPath = (os == "linux")? "aws_linux.json": "aws_windows.json"
                }else if(cloudProvider == "azure"){
                    regionsParamName = "AZURE_REGIONS"
                    infraDefaultJsonInputPath = (os == "linux")? "azure_linux.json": "azure_windows.json"
                }
                
                regionsList = params[regionsParamName].split(",").collect { it.trim() }
                regionsListString = pipelineHelper.listMovetoTop(regionsList,infrastructureParams['REGION'] ? infrastructureParams['REGION'] : null).join("\n")
                infrastructureSpec = readFile(file: "${defaultInputsDir}/${infraDefaultJsonInputPath}")
                infrastructureSpec = new groovy.text.SimpleTemplateEngine()
                            .createTemplate(infrastructureSpec)
                            .make([customer:customerName,enviroment:enviromentName])
                            .toString()

                infrastructureInputParams = [
                        [ 
                            $class: 'CredentialsParameterDefinition',
                            name: 'CLOUD_PROVIDER_CREDENTIAL_ID',
                            defaultValue: infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] ? infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] : "",
                            description: 'Select cloud provider credentials.'
                        ],
                        [ 
                            $class: 'ChoiceParameterDefinition',
                            name: 'REGION',
                            choices: regionsListString,
                            description: 'Your azure region.',
                            trim: true
                        ],
                        [ 
                            $class: 'TextParameterDefinition',
                            name: 'INFRASTRUCTURE',
                            defaultValue: infrastructureParams['INFRASTRUCTURE'] ? JsonOutput.prettyPrint(infrastructureParams['INFRASTRUCTURE'].toString()) : infrastructureSpec,
                            description: 'Customize infrastructure',
                            trim: true
                        ] 
                    ]
            }

            
            def newInfrastructureInputParams = input(message: 'Specify necessary infrastructure configuration.', parameters: infrastructureInputParams)
            def jsonForValidate = (cloudProvider == "on-prem")? newInfrastructureInputParams: newInfrastructureInputParams['INFRASTRUCTURE']
            while(!pipelineHelper.isValidJSON(jsonForValidate)){ // if NOT a valid json
                newInfrastructureInputParams = input(message: 'Specify necessary infrastructure configuration.', parameters: infrastructureInputParams)
                jsonForValidate = (cloudProvider == "on-prem")? newInfrastructureInputParams: newInfrastructureInputParams['INFRASTRUCTURE']
            }

            if(cloudProvider == "on-prem"){
                writeFile(file: "${workspacePath}/${customerEnvDir}/${infrastructure}/infrastructure-details.json", text: newInfrastructureInputParams, encoding: 'utf-8')
            }else{
                newInfrastructureInputParams.each{ param, value -> 
                    if(param == 'INFRASTRUCTURE'){
                        infrastructureParams[param] = new JsonSlurper().parseText(value)
                    }
                    else{
                        infrastructureParams[param] = value
                    }
                }
                writeJSON(file: "${customerEnvDir}/deployment-params.json", json: deploymentParams)
            }
        }
        /* 
         * Prompt user to enter configure nodes selections
        */
        pipelineHelper.stage('Capture Parameters for Configure Nodes phase', shouldConfigureNodes) {
            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def enactorSuiteParams = deploymentParams['enactor-suite-params']

            def nodeConfigMode = [
                    [
                        $class: 'ChoiceParameterDefinition',
                        name: 'NODE_CONFIG_MODE',
                        choices: 'normal\ncis-hardened', 
                        description: 'Select the mode for Configure Nodes phase.'
                    ]
                ]
            enactorSuiteParams['NODE_CONFIG_MODE'] = input(message: 'Provide the parameters for Configure Nodes phase.\n Select "cis-hardened" if using cis hardened OS images for infrastructure, else select "normal"', parameters: nodeConfigMode)
            writeJSON(file: "${customerEnvDir}/deployment-params.json", json: deploymentParams)
        }
        /* 
         * Prompt user to enter deployment specifications
        */
        pipelineHelper.stage('Capture Parameters to Deploy Enactor Application Suite', shouldDeployEnactorSuite) {
            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def enactorSuiteParams = deploymentParams['enactor-suite-params']

            // Obtain the first set of params required for deploy enactor suite job.
            def firstLevelInputParams = [
                [
                    $class: 'StringParameterDefinition',
                    name: 'CONTAINER_REGISTRY',
                    defaultValue: enactorSuiteParams['CONTAINER_REGISTRY'] ? enactorSuiteParams['CONTAINER_REGISTRY'] : 'enactordev-on.azurecr.io',
                    description: 'Docker image registry which Enactor images are pulled from.',
                    trim: true
                ],
                [
                    $class: 'StringParameterDefinition',
                    name: 'ENACTOR_DOCKER_IMAGE_VERSION',
                    defaultValue: enactorSuiteParams['STANDARD_DOCKER_VERSION'] ? enactorSuiteParams['STANDARD_DOCKER_VERSION'] : 'latest',
                    description: 'Select the Enactor standard docker image version.',
                    trim: true
                ],
                [ 
                    $class: 'CredentialsParameterDefinition',
                    name: 'DOCKER_REGISTRY_CREDENTIALS',
                    defaultValue: enactorSuiteParams['DOCKER_REGISTRY_CREDENTIALS'] ? enactorSuiteParams['DOCKER_REGISTRY_CREDENTIALS'] : "",
                    required: true,
                    description: 'Select docker registry credentials.'
                ],
                [
                    $class: 'BooleanParameterDefinition',
                    name: 'ENABLE_SERVICE_SELECTION',
                    defaultValue: enactorSuiteParams['ENABLE_SERVICE_SELECTION'] || false,
                    description: 'Tick the check box if only a selective set of services should be deployed.'
                ],
                [
                    $class: 'BooleanParameterDefinition',
                    name: 'ENABLE_SERVICE_CUSTOMIZATION',
                    defaultValue: enactorSuiteParams['ENABLE_SERVICE_CUSTOMIZATION'] || false,
                    description: 'Tick the check box if the selected services need to be customized before deployment. (i.e to set contraints, to change the docker registry and etc)'
                ],
                [
                    $class: 'BooleanParameterDefinition',
                    name: 'ENABLE_ENV_OVERRIDE',
                    defaultValue: enactorSuiteParams['ENABLE_ENV_OVERRIDE'] || false,
                    description: 'Tick the check box if Enactor configuration environment variables should be updated before deploy.'
                ],
                [
                    $class: 'BooleanParameterDefinition',
                    name: 'FORCE_REDEPLOYMENT',
                    defaultValue: (enactorSuiteParams['FORCE_REDEPLOYMENT'] == null) ? true : enactorSuiteParams['FORCE_REDEPLOYMENT'],
                    description: 'Tick the check box if it should be a redeployment (required when changing the .env files). Keep the box unticked if it should be only an update (Only when Enactor version upgrade and changing the number of replicas).'
                ],
                [
                    $class: 'BooleanParameterDefinition',
                    name: 'IS_NFS_VOLUMES',
                    defaultValue: enactorSuiteParams['IS_NFS_VOLUMES'] || false,
                    description: 'Tick the check box if network shared volumes should be used for the services.'
                ]
            ]
            def newEnactorSuiteParams = input(message: 'Provide the parameters for Enactor application suite deployment.', parameters: firstLevelInputParams)

            // Remove getting 'STANDARD_MYSQL_VERSION' as a user-input.
            enactorSuiteParams['STANDARD_MYSQL_VERSION'] = 'latest'
            newEnactorSuiteParams.each{ param, value -> 
                if (param == 'ENACTOR_DOCKER_IMAGE_VERSION') {
                    // This to change STANDARD_DOCKER_VERSION to ENACTOR_DOCKER_IMAGE_VERSION at the Jenkins UI
                    // while ensuring the backward compatibility with older environments as well.
                    enactorSuiteParams['STANDARD_DOCKER_VERSION'] = value
                } else {
                    enactorSuiteParams[param] = value
                }
            }

            // Get the service list to be deployed.
            def defaultServiceList = readJSON(file: "${stacksDir}/${os}/stacks/available-services-list.json")
            def serviceConfigMapPath = "${stacksDir}/${os}/stacks/service-config-map.json"
            def serviceConfigMap = readJSON(file: serviceConfigMapPath)
            def serviceList = enactorSuiteParams['service-list']
            if (enactorSuiteParams['ENABLE_SERVICE_SELECTION']) {
                def serviceListParams = []
                if (serviceList) {
                    for (def ii=0; ii<defaultServiceList.size(); ii++) {
                        serviceListParams << [
                            $class: 'BooleanParameterDefinition',
                            name: defaultServiceList[ii],
                            defaultValue: serviceList.contains(defaultServiceList[ii]),
                            description: serviceConfigMap[defaultServiceList[ii]]['description']
                        ]
                    }    
                } else {
                    for (def ii=0; ii<defaultServiceList.size(); ii++) {
                        serviceListParams << [
                            $class: 'BooleanParameterDefinition',
                            name: defaultServiceList[ii],
                            defaultValue: false,
                            description: serviceConfigMap[defaultServiceList[ii]]['description']
                        ]
                    }
                }

                def newServiceList = input(id: 'userInput', message: 'Select the services to be deployed', parameters: serviceListParams)
                def selectedServices = []
                newServiceList.each{ service, isTicked -> 
                    if (isTicked) {
                        selectedServices << service
                    } 
                }
                enactorSuiteParams['service-list'] = selectedServices
            } else {
                enactorSuiteParams['service-list'] = serviceList ? serviceList : defaultServiceList
            }
            
            // Get the service customized feature values of selected services.
            def selectedServices = enactorSuiteParams['service-list']
            def serviceFeatureMap = enactorSuiteParams['service-feature-map']
            def serviceFeatureMapKeys = serviceFeatureMap ? serviceFeatureMap.keySet() as List : []
            if (enactorSuiteParams['ENABLE_SERVICE_CUSTOMIZATION']) {  
                selectedServices.each { service ->
                    def serviceFeatureInputParams = []

                    def portPublishingMode = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['PORT_PUBLISHING_MODE'] 
                            : (os == 'windows') ? 'host' : 'ingress'
                    serviceFeatureInputParams << [
                        $class: 'ChoiceParameterDefinition',
                        name: 'PORT_PUBLISHING_MODE',
                        choices: "${portPublishingMode}\n${(portPublishingMode == 'ingress') ? 'host' : 'ingress'}",
                        description: 'Select the port publishing mode required for the service.'
                    ]

                    if ( service != 'file-beat-stack.yml' && service != 'metric-beat-stack.yml' ) {    
                        def replicas = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['REPLICAS'] : 1
                        serviceFeatureInputParams << [
                            $class: 'StringParameterDefinition',
                            name: 'REPLICAS',
                            defaultValue: "${replicas}",
                            description: 'Add the number of replicas required. If host mode port publishing selected, replicas must be 1.',
                            trim: true
                        ]                                             
                    }

                    def constraints = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['CONSTRAINTS'].join(',').replace('"', '') : ''
                    serviceFeatureInputParams << [
                        $class: 'StringParameterDefinition',
                        name: 'CONSTRAINTS',
                        defaultValue: constraints,
                        description: 'Add comma separated constraints to be enforced on the service. E.g node.labels.ecom==true',
                        trim: true
                    ]

                    def imagePath = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['IMAGE_PATH'] : ''
                    serviceFeatureInputParams << [
                            $class: 'StringParameterDefinition',
                            name: 'IMAGE_PATH',
                            defaultValue: "${imagePath}",
                            description: "The image path for ${service} where image-path is the middle part of container URL: '<registry>/<imagePath>:<tag>'",
                            trim: true
                    ]

                    def imageTag = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['IMAGE_TAG'] : ''
                    imageTag = imageTag?.trim() ? imageTag : (service.contains("sql") ? enactorSuiteParams['STANDARD_MYSQL_VERSION'] : enactorSuiteParams['STANDARD_DOCKER_VERSION'])
                    serviceFeatureInputParams << [
                            $class: 'StringParameterDefinition',
                            name: 'IMAGE_TAG',
                            defaultValue: "${imageTag}",
                            description: "The image tag for ${service}",
                            trim: true
                    ]

                    def userInputServiceFeatures = input( id: service, message: "Give the service feature values for the ${service} service.", 
                            parameters: serviceFeatureInputParams)
                    userInputServiceFeatures.each{ feature, value -> 
                        switch(feature) {
                            case 'PORT_PUBLISHING_MODE':
                                portPublishingMode = value
                                break;
                            case 'REPLICAS':
                                replicas = value
                                break;
                            case 'CONSTRAINTS':
                                constraints = value
                                break;
                            case 'IMAGE_PATH':
                                imagePath = value
                                break;
                            case 'IMAGE_TAG':
                                imageTag = value
                                break;
                        }
                    }

                    // Write back the obtained feature values
                    constraints = constraints ? constraints.split(',') as List : []
                    constraints.eachWithIndex{item, i -> constraints[i] = item.trim()}
                    def featureObject = [
                            'IMAGE_PATH'           : imagePath,
                            'IMAGE_TAG'            : imageTag,
                            'PORT_PUBLISHING_MODE': portPublishingMode,
                            'REPLICAS'            : replicas ? replicas as Integer : 1,
                            'CONSTRAINTS'         : constraints
                    ]
                    serviceFeatureMap.put(service, featureObject)
                }
            } else {
                selectedServices.each { service ->
                    def portPublishingMode = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['PORT_PUBLISHING_MODE'] 
                            : (os == 'windows') ? 'host' : 'ingress'
                    def replicas = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['REPLICAS'] : 1
                    def constraints = serviceFeatureMapKeys.contains(service) ? serviceFeatureMap[service]['CONSTRAINTS'] : []
                    def imagePath = ''
                    def imageTag = ''
                    if (serviceFeatureMapKeys.contains(service)) {
                        imagePath = serviceFeatureMap[service]['IMAGE_PATH']?.trim() ? serviceFeatureMap[service]['IMAGE_PATH'] : ''
                        imageTag = serviceFeatureMap[service]['IMAGE_TAG']?.trim() ? serviceFeatureMap[service]['IMAGE_TAG'] : ''
                    }

                    def featureObject = [
                            'IMAGE_PATH'          : imagePath,
                            'IMAGE_TAG'           : imageTag,
                            'PORT_PUBLISHING_MODE': portPublishingMode,
                            'REPLICAS'            : replicas,
                            'CONSTRAINTS'         : constraints
                    ]
                    serviceFeatureMap.put(service, featureObject)
                }
            }

            // Write and commit the deployment-params.json.
            writeJSON(file: "${customerEnvDir}/deployment-params.json", json: deploymentParams)

            // Get the .env file set required for the services and copy them into environment deployment directory.
            def deploymentDirPath = "${workspacePath}/${customerEnvDir}/${deployment}"
            sh "mkdir -p ${customerEnvDir}/${deployment}/envs" // No need to check for existence with -p option.

            def requiredEnvFiles = []
            selectedServices.each{ service ->
                serviceConfigMap[service]['env_files'].each{ envFile ->
                    requiredEnvFiles << envFile
                }
            }

            requiredEnvFiles = requiredEnvFiles as Set // remove duplicates

            requiredEnvFiles.each{ configFileName ->
                def envsAbsolutePath = "${workspacePath}/${customerEnvDir}/${deployment}/envs"
                def envsRelativePath = "${customerEnvDir}/${deployment}/envs"
                def configFileAbsolutePath = "${envsAbsolutePath}/${configFileName}"
                
                if ( !fileExists(file: configFileAbsolutePath) ) {
                    
                    def relConfigFile = new File("${envsRelativePath}/${configFileName}")
                    sh "mkdir -p ${relConfigFile.getParentFile().getPath()}" // No need to check for existence with -p option.

                    sh "cp -a ${stacksDir}/${os}/stacks/envs/${configFileName} ${customerEnvDir}/${deployment}/envs/${configFileName}"
                }
            }

            // Allow the editing of .env files if requested.
            if (enactorSuiteParams['ENABLE_ENV_OVERRIDE']) {
                def envPath = "${deploymentDirPath}/envs"
                def envTextParamList = []
                requiredEnvFiles.each{ file ->
                    def envFilePath = "${envPath}/${file}"
                    def envContent =  readFile(file: envFilePath, encoding: 'utf-8')
                    envTextParamList << [
                        $class: 'TextParameterDefinition',
                        name: file,
                        defaultValue: envContent,
                        description: "Edit ${file} as required."
                    ]
                }
                def userInputEnvFileList = input(id: 'userInput', message: 'Edit the envs as required.', parameters: envTextParamList)
                userInputEnvFileList.each{ file, content -> 
                    def envFilePath = "${envPath}/${file}"
                    // TODO validate env file content.
                    writeFile(file: envFilePath, text: content, encoding: 'utf-8')
                }
            }
        }

        stage('Commit captured data'){
            if(svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customerPath}/${enviromentName}")){ // if env exists in svn
                println("================================= env exists")
                svnHelper.add_and_commit(svnCredentialID, customerEnvDir, true)
            }
            else if(svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customerPath}")){ // if customer and path exists
                println("================================= customer and path exists")
                svnHelper.add_and_commit(svnCredentialID, "${customersDir}/${customerPath}", true)
            }
            else{
                println("================================= new customer")
                svnHelper.add_and_commit(svnCredentialID, "${customersDir}", true)
            }
        }

        /* 
         * Invoke downstream job to create/update infrastructure based on the captured inputs
        */
        pipelineHelper.stage('Create or Update Infrastructure', shouldCreateInfra && cloudProvider != "on-prem") {
            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def infrastructureParams = deploymentParams['infrastructure-params']
            def enactorSuiteParams = deploymentParams['enactor-suite-params']
            def serviceFilesList = enactorSuiteParams['service-list']
            def machinesJSON = infrastructureParams['INFRASTRUCTURE']['machines']
            
            def securityGroupsJSON = pipelineHelper.generateSecurityGroupsJson(serviceFilesList, servicePorts, machinesJSON, cloudProvider)

            println(JsonOutput.toJson(securityGroupsJSON))

            def buildParameters = [
                string( name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialID),
                string( name: 'CUSTOMER_NAME',   value: customerPath ),
                string( name: 'ENV_NAME',        value: enviromentName ),
                string( name: 'PROVIDER',        value: infrastructureParams['provider']),
                string( name: 'CLUSTER_PROFILE', value: JsonOutput.toJson(infrastructureParams['INFRASTRUCTURE']) ?: ''),
                string( name: 'SECURITY_GROUPS', value: JsonOutput.toJson(securityGroupsJSON) ?: ''),
                string( name: 'REGION',          value: infrastructureParams['REGION'] ?: ''),
                string( name: 'CLOUD_PROVIDER_CREDENTIAL_ID',       value: infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] ?: '')
            ]
            
            build(job: 'deployment-tasks/create-update-infrastructure', propagate: true, wait: true, parameters: buildParameters)
            
        }
        /* 
         * Invoke downstream job to install required software coponents
        */
        pipelineHelper.stage('Configure Nodes', shouldConfigureNodes) {
            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def enactorSuiteParams = deploymentParams['enactor-suite-params']
            def buildParameters = [
                string( name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialID),
                string(name: 'CUSTOMER_NAME', value: customerPath), 
                string(name: 'ENV_NAME', value: enviromentName),
                string(name: 'NODE_CONFIG_MODE', value: enactorSuiteParams['NODE_CONFIG_MODE'])
                ]
            build(job: 'deployment-tasks/configure-nodes', propagate: true, wait: true, parameters: buildParameters)
        }

        /* 
         * Invoke downstream job to setup swarm cluster
        */
        pipelineHelper.stage('Setup Swarm', shouldSetupSwarm) {
            def buildParameters = [
                string(name: 'CUSTOMER_NAME', value: customerPath), 
                string(name: 'ENV_NAME', value: enviromentName)
                ]
            build(job: 'deployment-tasks/setup-swarm', propagate: true, wait: true, parameters: buildParameters)
        }


        /* 
         * Invoke downstream to perform the deployment
        */
        pipelineHelper.stage('Deploy Enactor Suite', shouldDeployEnactorSuite) {
            try {
                svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customerPath}/${enviromentName}", "${customerEnvDir}")
                leaderIP = pipelineHelper.getManagerIP(readFile(file:"${customerEnvDir}/${infrastructure}/infrastructure-details.json"))
            } catch (Exception ex) {
                println("Couldn't retrive swarm manager IP : ${ex}")
            } 
            
            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def enactorSuiteParams = deploymentParams['enactor-suite-params']
            def buildParameters = [
                string( name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialID),
                string(name: 'CUSTOMER_NAME', value: customerPath), 
                string(name: 'ENV_NAME', value: enviromentName),
                string(name: 'CONTAINER_REGISTRY', value: enactorSuiteParams['CONTAINER_REGISTRY']),
                string(name: 'STANDARD_DOCKER_VERSION', value: enactorSuiteParams['STANDARD_DOCKER_VERSION']),
                string(name: 'STANDARD_MYSQL_VERSION', value: enactorSuiteParams['STANDARD_MYSQL_VERSION']),
                string(name: 'DOCKER_REGISTRY_CREDENTIALS', value: enactorSuiteParams['DOCKER_REGISTRY_CREDENTIALS']),
                string(name: 'PUBLIC_IP_ADDRESS', value: leaderIP? leaderIP:'172.17.0.1'),
                booleanParam(name: 'FORCE_REDEPLOYMENT', value: (enactorSuiteParams['FORCE_REDEPLOYMENT'] == null) ? true : enactorSuiteParams['FORCE_REDEPLOYMENT'])
                ]
            build(job: 'deployment-tasks/deploy-enactor-suite', propagate: true, wait: true, parameters: buildParameters)
        }
        /* 
         * Acknoledge user on EM url
        */
        stage("Output"){
            try {
                if(!leaderIP){
                   try {
                        svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customerPath}/${enviromentName}", "${customerEnvDir}")
                        leaderIP = pipelineHelper.getManagerIP(readFile(file:"${customerEnvDir}/${infrastructure}/infrastructure-details.json"))
                    } catch (Exception ex) {
                        println("Couldn't retrive swarm manager IP : ${ex}")
                    } 
                }
                println "=========================================================================="
                println "http://${leaderIP}:39830/WebMaintenance/Layout/Standard/DesktopLayout.layout"
                println "=========================================================================="
            } catch (ignore) {
                println("Couldn't retrive swarm manager IP")
            } 
        }
    } finally {
        // cleanWs()
    }
}
