#!groovy

/*
 * This script is responsible for running a transaction processing performance test on
 * a given EM version or on a given feature branch. This pipeline will first create 2 VMs
 * named testVM and TargetVM where EM will be deployed in TargetVM. Then the TestVM will be
 * used to generate and send transactions to the TargetVM.
 *
 * One all transactions have been processed, this pipeline will analyze the transaction
 * processing performance and publish those results on a Kibana dashboard.
 */

import hudson.model.*
import groovy.json.JsonOutput

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper
def testSuiteHelper

// Following versions should be updated per release
String TEST_SUITE_IMAGE_TAG = 'dev'
String ELASTICSEARCH_VERSION = '6.8.0'
String TEST_SUITE_DOCKER_IMAGE = "enactorsandbox.azurecr.io/emperformancetestsuite/test-suite:${TEST_SUITE_IMAGE_TAG}"
String TEST_SUITE_CONTAINER = 'test-suite'
String METRICBEATS_CONTAINER = 'metricbeats'

def svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
def svnCustomersRepo = env.CUSTOMERS_SVN_LOCATION

def selfServiceJenkinsFolder = env.SELF_SERVICE_JENKINS_JOBS_FOLDER_DEV ? env.SELF_SERVICE_JENKINS_JOBS_FOLDER_DEV : ''
def registerEnvJob = "${selfServiceJenkinsFolder}enactor-swarm-deployment/deployment-tasks/register-environment"
def createOrUpdateInfraJob = "${selfServiceJenkinsFolder}enactor-swarm-deployment/deployment-tasks/create-update-infrastructure"
def configureNodesJob = "${selfServiceJenkinsFolder}enactor-swarm-deployment/deployment-tasks/configure-nodes"
def setupSwarmJob = "${selfServiceJenkinsFolder}enactor-swarm-deployment/deployment-tasks/setup-swarm"
deployServicesJob = "${selfServiceJenkinsFolder}enactor-swarm-deployment/deployment-tasks/deploy-enactor-suite"
def removeInfraJob = "${selfServiceJenkinsFolder}enactor-swarm-deployment/Remove-Customer-Environment"

/*============= Constants ================*/
def EMA_PORT = 39830
def EMP_PORT = 39832
def EMS_PORT = 39833
def MYSQL_DB_PORT = 3326
def MSSQL_DB_PORT = 2433
def DB_DATABASE = 'enactorem'

def servicePorts = [
        ema  : [port: "39830"],
        emp  : [port: "39832"],
        ems  : [port: "39833"],
        emr  : [port: "39831"],
        emc  : [port: "39856"],
        mssql: [port: "${MSSQL_DB_PORT}"]
]
servicePorts['mysql-em'] = [port: "${MYSQL_DB_PORT}"]

String OS_LINUX = "linux"
String OS_WINDOWS = "windows"
String DB_MYSQL = "MySQL"
String DB_MSSQL = "MSSQL"

String DOCKER_COMPOSE_DIRECTORY = "docker-compose"
String MSSQL_DIRECTORY = "mssql"
String HOSTS_FILENAME = "infrastructure-details.json"
String DEPLOYMENT_PARAMS_FILENAME = "deployment-params.json"

String svnCredentialsId = params.SVN_CREDENTIALS_ID

/* Simulation Information */
def simulationDuration = "${params.SIMULATION_DURATION}"
def simulationNoOfDevices = "${params.SIMULATION_NO_OF_DEVICES}"
def simulationNoOfThreads = "${params.SIMULATION_NO_OF_THREADS}"
def simulationTransactionType = "${params.SIMULATION_TRANSACTION_TYPE}"

/* Infrastructure Information */
def targetCustomer = params.CUSTOMER_NAME
def targetProvider = params.PROVIDER
def targetEnvironment = params.ENV_NAME
def targetDockerTag = params.STANDARD_DOCKER_VERSION
def targetReleaseVersion = targetDockerTag
def os = OS_LINUX
def dbType = params.DATABASE_TYPE
def clusterProfile

/* AWS/Azure Credentials and Cluster Profiles */
String cloudProviderCredentialsId = params.CLOUD_PROVIDER_CREDENTIALS
String sandboxRegistryCredentialsId = params.SANDBOX_REGISTRY_CREDENTIALS
String region = params.REGION

/* Feature Branch? */
boolean isFeatureBranch = params.TEST_FEATURE_BRANCH.toBoolean()
String featureName

String envDirPrefix = params.ENV_DIRECTORY_PREFIX
String suffixedCustomer = "${targetCustomer}/${envDirPrefix}"

/* Environment and Infra */
String customersDirectory = "customers"
String customerDirectory = "${targetCustomer}"
String environmentDirectory = "${suffixedCustomer}/${targetEnvironment}"
String environmentInfraDirectory = "${environmentDirectory}/infrastructure"
String environmentDeploymentDirectory = "${environmentDirectory}/deployment"
String ansibleDirectory = "ansible"
String testSuiteDirectory = "test_suite"
String sampleDeploymentsDirectory = "${testSuiteDirectory}/default_deployments"
String dockerComposeDirectory = "${testSuiteDirectory}/${DOCKER_COMPOSE_DIRECTORY}"
String msSqlDirectory = "${testSuiteDirectory}/${MSSQL_DIRECTORY}"
String stacksDir = "deployment-architectures"

/* EM Database Information */
def emDatabase = [:]
/* Test VM - Where message generation happen */
def testVM = [:]
// Where EMP is running. i.e. Target EM server
def targetVM = [:]

/* EM Information */
def emServer = [:]
String emServerQueue = params.SERVER_QUEUE

/* Elastic Search and Kibana Configs */
def esServer = [:]
esServer.host = params.ELASTIC_SEARCH_HOST
esServer.port = params.ELASTIC_SEARCH_PORT
esServer.scheme = params.ELASTIC_SEARCH_SCHEME

def kibanaServer = [:]
kibanaServer.host = params.KIBANA_HOST
kibanaServer.port = params.KIBANA_PORT

/* Container Registry */
String containerRegistryCredentialsId = params.CONTAINER_REGISTRY_CREDENTIALS_ID

/* Licence.xml Content and Demo Data URL */
String licenceFileContent = params.LICENCE_FILE_CONTENT
String dataFileUrl = params.DATA_FILE_URL

/* Destroy Infra */
boolean destroyInfrastructure = params.DESTROY_INFRASTRUCTURE
boolean forceDestroyInfrastructure = params.FORCE_DESTROY_INFRASTRUCTURE
/* Skip data Upload */
boolean skipDataUpload = params.SKIP_DATA_UPLOAD

/*=========================================================================================*/
/*=                                      Jenkins Pipeline                                 =*/
/*=========================================================================================*/

node('master') {
    svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
    testSuiteHelper = load("$WORKSPACE@script/helpers/test_suite_helpers.groovy")
}

node("${env.SELF_SERVICE_NODE}") {
    cleanWs()

    boolean resultsPublished = false
    String hostsFile = "${WORKSPACE}/${environmentInfraDirectory}/${HOSTS_FILENAME}"
    String simulationName = "${targetCustomer}_${targetEnvironment}_${targetReleaseVersion}"

    try {
        /**
         * Checkout required svn directories
         */
        stage('Init and Checkout') {
            svnHelper.checkout(svnCredentialsId, svnSelfServiceAppReleaseRepo, ansibleDirectory, ansibleDirectory)
            svnHelper.checkout(svnCredentialsId, svnSelfServiceAppReleaseRepo, "${stacksDir}/", "${stacksDir}")
            svnHelper.checkout(svnCredentialsId, svnSelfServiceAppReleaseRepo, testSuiteDirectory, testSuiteDirectory)

            if (isFeatureBranch) {
                echo "Testing a feature branch"

                def data = input(message: 'Please enter the feature branch name', parameters: [
                        [
                                $class      : 'StringParameterDefinition',
                                name        : 'FEATURE_BRANCH_NAME',
                                defaultValue: 'trunk_feature_inbound-document-content',
                                description : 'Name of the feature branch to be tested',
                                trim        : true
                        ],
                        [
                                $class      : 'StringParameterDefinition',
                                name        : 'DOCKER_TAG',
                                defaultValue: "${targetDockerTag}",
                                description : 'Tag of the docker images of the feature branch',
                                trim        : true
                        ]
                ])

                echo "${data}"

                featureName = data.FEATURE_BRANCH_NAME
                targetDockerTag = data.DOCKER_TAG
                targetReleaseVersion = "${featureName}-${targetDockerTag}"
                echo "Feature Name: ${featureName}"

                featureName = featureName.replace("trunk_feature_", "TRUNK-feature-")
                featureName = featureName.replace("2_3_feature_", "2.3.feature-")
                featureName = featureName.replace("2_4_feature_", "2.4.feature-")
                featureName = featureName.replace("2_5_feature_", "2.5.feature-")
                featureName = featureName.toLowerCase()
                echo "Processed Feature Name: ${featureName}"
            }
        }

        /**
         * Deploys EM and Services. Makes use of RegisterEnvironment, Create/Update Infrastructure,
         * Configure Nodes/Swarm and Deploy Enactor Suite jobs to achieve the end goal.
         */
        stage('Deploy EM') {
            boolean environmentExists = svnHelper.isSvnPathExists(svnCredentialsId, svnCustomersRepo, environmentDirectory)
            if (environmentExists) {
                echo "Environment ${environmentDirectory} already exists"
            } else {
                build(
                        job: "${registerEnvJob}",
                        propagate: true, wait: true,
                        parameters: [
                                string(name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialsId),
                                string(name: 'CUSTOMER_NAME', value: targetCustomer),
                                string(name: 'ENV_NAME', value: targetEnvironment),
                                string(name: 'ACTION', value: "create"),
                                string(name: 'PROVIDER', value: targetProvider),
                                string(name: 'OS_TYPE', value: os)
                        ]
                )
            }

            echo "Updating deployment params in: ${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}"
            svnHelper.checkout(svnCredentialsId, svnCustomersRepo, customerDirectory, customerDirectory)
            def deploymentParams = readJSON(file: "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}")

            def sampleDeploymentParams = readJSON(file: "${sampleDeploymentsDirectory}/${targetProvider}-${os}-${DEPLOYMENT_PARAMS_FILENAME}")
            sampleDeploymentParams['infrastructure-params']['REGION'] = region
            sampleDeploymentParams['infrastructure-params']['CLOUD_PROVIDER_CREDENTIAL_ID'] = cloudProviderCredentialsId
            sampleDeploymentParams['infrastructure-params']['INFRASTRUCTURE']['resource_identifier'] = '' + "${targetCustomer}-${targetEnvironment}"

            sampleDeploymentParams['enactor-suite-params']['DOCKER_REGISTRY_CREDENTIALS'] = containerRegistryCredentialsId
            sampleDeploymentParams['enactor-suite-params']['STANDARD_DOCKER_VERSION'] = targetDockerTag
            if (isFeatureBranch) {
                String containerRepo = "enactor/feature/${featureName}"
                String osSuffix = os == OS_LINUX ? 'lin' : 'win'
                sampleDeploymentParams['enactor-suite-params']['service-feature-map'].each { serviceYml, propertyMap ->
                    if (!serviceYml.contains('sql')) {
                        String imageName = serviceYml.split('-')[0]
                        String imagePath = "${containerRepo}/${osSuffix}/${imageName}"
                        propertyMap['IMAGE_PATH'] = imagePath
                    }
                }
                echo "Processed service feature map: ${sampleDeploymentParams['enactor-suite-params']['service-feature-map']}"
            }

            deploymentParams['infrastructure-params'] = sampleDeploymentParams['infrastructure-params']
            deploymentParams['enactor-suite-params'] = sampleDeploymentParams['enactor-suite-params']

            writeJSON(file: "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}", json: deploymentParams)
            svnHelper.add_and_commit(svnCredentialsId, "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}", true)

            clusterProfile = deploymentParams['infrastructure-params']['INFRASTRUCTURE']
            // Services to be deployed
            def selectedServices = deploymentParams['enactor-suite-params']['service-list'].collect()

            // TODO Cleanup
            def deploymentDir = new File("${WORKSPACE}/${environmentDeploymentDirectory}")
            def serviceConfigMapPath = "${stacksDir}/${os}/stacks/service-config-map.json"
            def serviceConfigMap = readJSON(file: serviceConfigMapPath)
            def deploymentDirExists = deploymentDir.exists()
            if (!deploymentDirExists) {
                sh "mkdir -p ${environmentDeploymentDirectory}/envs"
                sh "cp -a ${stacksDir}/${os}/stacks/envs/common.env ${environmentDeploymentDirectory}/envs/common.env"
            }
            def existingEnvFiles = pipelineHelper.getFileSystemItemList("${environmentDeploymentDirectory}/envs")
            def requiredEnvFiles = []
            selectedServices.each { service ->
                serviceConfigMap[service]['env_files'].each { envFile ->
                    requiredEnvFiles << envFile
                }
            }
            requiredEnvFiles = requiredEnvFiles as Set
            existingEnvFiles = existingEnvFiles as Set
            def shoulCopyEnvFiles = requiredEnvFiles - existingEnvFiles
            shoulCopyEnvFiles.each { envFileName ->
                sh "cp -a ${stacksDir}/${os}/stacks/envs/${envFileName} ${environmentDeploymentDirectory}/envs/${envFileName}"
            }
            // Commit the deployment dir changes.
            svnHelper.add_and_commit(svnCredentialsId, environmentDeploymentDirectory, true)

            def machinesJSON = deploymentParams['infrastructure-params']['INFRASTRUCTURE']['machines']
            def serviceListWithMSSQL = selectedServices.collect()
            serviceListWithMSSQL += "mssql-stack.yml"
            def securityGroupsJSON = pipelineHelper.generateSecurityGroupsJson(serviceListWithMSSQL, servicePorts, machinesJSON, targetProvider)

            build(job: "${createOrUpdateInfraJob}", propagate: true, wait: true,
                    parameters: [
                            string(name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialsId),
                            string(name: 'CUSTOMER_NAME', value: suffixedCustomer),
                            string(name: 'ENV_NAME', value: targetEnvironment),
                            string(name: 'PROVIDER', value: targetProvider),
                            string(name: 'CLUSTER_PROFILE', value: JsonOutput.toJson(clusterProfile)),
                            string(name: 'SECURITY_GROUPS', value: JsonOutput.toJson(securityGroupsJSON)),
                            string(name: 'REGION', value: region),
                            string(name: 'CLOUD_PROVIDER_CREDENTIAL_ID', value: cloudProviderCredentialsId)
                    ]
            )

            echo "Reading infrastructure details from ${environmentDirectory}"
            svnHelper.checkout(svnCredentialsId, svnCustomersRepo, customerDirectory, customerDirectory)

            def result = readJSON(file: "${environmentInfraDirectory}/${HOSTS_FILENAME}")

            targetVM = testSuiteHelper.getNodeFromInventory(result, targetCustomer, targetEnvironment, "manager_nodes")
            testVM = testSuiteHelper.getNodeFromInventory(result, targetCustomer, targetEnvironment, "tester_nodes")

            if (!targetVM.host || !testVM.host) {
                error "Both TestVM ($testVM) and TargetVM ($targetVM) should be available"
            }

            emServer = testSuiteHelper.getEMServer(targetVM, EMA_PORT, EMP_PORT, EMS_PORT, emServerQueue)
            echo "EM Server: ${emServer}"

            emDatabase = testSuiteHelper.getEMDatabase(dbType, targetVM.host, DB_DATABASE)
            echo "Database host: ${emDatabase.host} -> ${emDatabase}"

            echo "Target VM: ${targetVM.host}"
            echo "Test VM: ${testVM.host}"

            // Right now, we only check if hosts filename available.
            boolean infraAvailable = svnHelper.isSvnPathExists(svnCredentialsId, svnCustomersRepo, "${environmentInfraDirectory}/${HOSTS_FILENAME}")

            // Check services available
            if (infraAvailable && !testSuiteHelper.isServicesUp(emServer)) {
                // If services are not available, We redo the deployment
                infraAvailable = false
            }

            if (!infraAvailable) {
                echo 'Infrastructure/Services not available. Installing swarm'

                build(job: "${configureNodesJob}", propagate: true, wait: true, parameters: [
                        string(name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialsId),
                        string(name: 'CUSTOMER_NAME', value: suffixedCustomer),
                        string(name: 'ENV_NAME', value: targetEnvironment)
                ])

                build(job: "${setupSwarmJob}", propagate: true, wait: true, parameters: [
                        string(name: 'CUSTOMER_NAME', value: suffixedCustomer),
                        string(name: 'ENV_NAME', value: targetEnvironment)
                ])

                // Here we need to setup MSSQL
                if (emDatabase.isMSSQL) {
                    echo "Setting up MSSQL"
                    testSuiteHelper.setupMSSQL(hostsFile, "${WORKSPACE}/${msSqlDirectory}", "${MSSQL_DIRECTORY}", emDatabase)
                    testSuiteHelper.updateEnvDBVariables(emDatabase, "${WORKSPACE}/${environmentDeploymentDirectory}/envs")
                    // Commit the deployment dir changes.
                    svnHelper.add_and_commit(svnCredentialsId, environmentDeploymentDirectory, true)
                }

                echo 'Enabling Performance Logging'
                testSuiteHelper.addEnvVariableToFile("${WORKSPACE}/${environmentDeploymentDirectory}/envs/emp.env", "ENABLE_PERFORMANCE_LOGGING", "true")
                svnHelper.add_and_commit(svnCredentialsId, environmentDeploymentDirectory, true)

                echo 'Deploying EMP first'
                sh "rm -rf ${customerDirectory}"
                svnHelper.checkout(svnCredentialsId, svnCustomersRepo, customerDirectory, customerDirectory)
                deploymentParams = readJSON(file: "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}")
                // selectedServices = deploymentParams['enactor-suite-params']['service-list'].collect()
                deploymentParams['enactor-suite-params']['service-list'] = ["emp-stack.yml", "mysql-em-stack.yml"]
                writeJSON(file: "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}", json: deploymentParams)
                svnHelper.add_and_commit(svnCredentialsId, "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}", true)
                // Now deploy EMP only
                deployServices(svnCredentialsId, suffixedCustomer, targetEnvironment, targetDockerTag, targetVM, containerRegistryCredentialsId)

                testSuiteHelper.waitForWebCoreStartup(emServer, hostsFile)

                echo 'Deploying All Services'
                deploymentParams['enactor-suite-params']['service-list'] = selectedServices
                writeJSON(file: "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}", json: deploymentParams)
                svnHelper.add_and_commit(svnCredentialsId, "${environmentDirectory}/${DEPLOYMENT_PARAMS_FILENAME}", true)
                // Now deploy all
                deployServices(svnCredentialsId, suffixedCustomer, targetEnvironment, targetDockerTag, targetVM, containerRegistryCredentialsId)
            } else {
                echo 'Project infrastructure already available. Skipping initializing swarm'
            }

            testSuiteHelper.waitForStartup(emServer, hostsFile)
        }

        /**
         * Uploads Enactor demo data to the newly deployed EM. Makes use of another jenkins job
         * which uses web services to upload demo data.
         */
        stage('Upload Data') {
            if (!skipDataUpload) {
                if (!licenceFileContent?.trim() || !dataFileUrl?.trim()) {
                    error 'Both licence.xml content and data file Url are required'
                }

                echo 'Uploading licence.xml and demo data'
                // No need to do retries here. Just try uploading, if fails, job should fail too
                build job: 'UploadDemoData', propagate: true, wait: true, parameters: [
                        string(name: 'EMP_HOST', value: emServer.empHost),
                        string(name: 'EMP_PORT', value: "${emServer.empPort}"),
                        string(name: 'EMS_HOST', value: emServer.emsHost),
                        string(name: 'EMS_PORT', value: "${emServer.emsPort}"),
                        string(name: 'WITH_RETRIES', value: "${true}"),
                        string(name: 'FILE_CONTENT', value: licenceFileContent),
                        string(name: 'FILE_URL', value: dataFileUrl)
                ]

                echo 'Data Import finished. Restarting EM for it to be fully functional'

                testSuiteHelper.restartEMServices(hostsFile)
                testSuiteHelper.waitForStartup(emServer, hostsFile)
            } else {
                echo 'Skipping data upload'
            }
        }

        /**
         * Start metricbeats in manager node
         */
        stage('Start Metric Beats') {
            echo "Preparing Docker-Compose in ${dockerComposeDirectory}"
            dir("${dockerComposeDirectory}") {
                pipelineHelper.execMaven("clean install -Dtest-suite.version=${TEST_SUITE_IMAGE_TAG} -Delasticsearch.version=${ELASTICSEARCH_VERSION}")
            }

            testSuiteHelper.copyFolder(hostsFile, "${WORKSPACE}/${dockerComposeDirectory}/target/", "./${DOCKER_COMPOSE_DIRECTORY}", "manager_nodes")

            echo "Starting Metric Beats in ${targetVM.host}"

            def envVariables = [
                    'MYSQL_USERNAME'     : "${emDatabase.user}",
                    'MYSQL_PASSWORD'     : "${emDatabase.password}",
                    'MYSQL_HOST'         : "${emDatabase.host}",
                    'MYSQL_PORT'         : "${emDatabase.port}",
                    'ELASTICSEARCH_HOSTS': "${esServer.host}:${esServer.port}",
                    'KIBANA_HOST'        : "${kibanaServer.host}:${kibanaServer.port}"
            ]

            testSuiteHelper.stopDockerCompose(hostsFile, DOCKER_COMPOSE_DIRECTORY, 'manager_nodes')
            testSuiteHelper.runDockerCompose(hostsFile, "./${DOCKER_COMPOSE_DIRECTORY}", METRICBEATS_CONTAINER, envVariables)
        }

        def testSuiteEnvVars = [
                simulation_name       : simulationName,
                duration              : simulationDuration,
                no_of_devices         : simulationNoOfDevices,
                no_of_threads         : simulationNoOfThreads,
                transaction_type      : simulationTransactionType,
                max_queue_length      : 10,
                inter_arrival_time    : 5,
                service_time          : 5,
                service_time_std      : 3,
                max_sale_items        : 10,
                target_provider       : "${targetProvider}",
                target_customer       : "${targetCustomer}",
                target_environment    : "${targetEnvironment}",
                target_release_version: "${targetReleaseVersion}",
                server_queue          : "${emServer.queueName}",
                elasticsearch_host    : esServer.host,
                elasticsearch_port    : esServer.port,
                elasticsearch_scheme  : esServer.scheme,
                kibana_base           : "http://${kibanaServer.host}:${kibanaServer.port}/",
                em_host               : emServer.empHost,
                em_port               : emServer.empPort,
                jdbc_driver           : emDatabase.driver,
                jdbc_url              : "\"${emDatabase.url}\"",
                jdbc_user             : emDatabase.user,
                jdbc_password         : emDatabase.password,
                operation             : 'generate'
        ]

        def testSuiteVolumes = [
                'testsuite': '/enactor/app/home'
        ]

        /**
         * Runs Performance Test. Since the test suite container is now capable of doing message
         * generation and result analyzing in two stages, we execute them in two steps.
         * In between those steps, we copy performance logs from EM to test suite.
         */
        stage('Run Performance Test') {
            echo "Starting message generation for ${targetVM.host}"
            testSuiteHelper.runDockerContainer(hostsFile, TEST_SUITE_DOCKER_IMAGE, TEST_SUITE_CONTAINER, testSuiteEnvVars, testSuiteVolumes, sandboxRegistryCredentialsId)

            while (true) {
                def state = testSuiteHelper.getContainerStatus("${hostsFile}", TEST_SUITE_CONTAINER)
                if (state.available) {
                    if (state.running) {
                        echo 'Container is still running'
                    } else {
                        if (state.exitCode != 0) {
                            echo "Container has failed with status code: ${state.exitCode}"
                            testSuiteHelper.fetch(hostsFile, '/var/lib/docker/volumes/testsuite/_data/test/logs/common.log', 'test-suite.log', 'tester_nodes', true, true)
                            error "Container has failed with exit code: ${state.exitCode}"
                        } else {
                            echo "Message generation finished successfully"
                            break
                        }
                    }
                } else {
                    error 'Container is not available'
                }
                sleep time: 60, unit: 'SECONDS'
            }
        }

        stage('Analyze Results') {
            echo "Fetching performance logs from EM"
            try {
                testSuiteHelper.fetchPerformanceLogs(hostsFile)
                testSuiteHelper.uploadPerformanceLogs(hostsFile)
            } catch (Exception e) {
                echo "Error occurred when downloading and uploading performance logs: ${e}"
            }

            echo 'Analyzing test results'
            testSuiteEnvVars.operation = 'analyze'
            testSuiteHelper.runDockerContainer(hostsFile, TEST_SUITE_DOCKER_IMAGE, TEST_SUITE_CONTAINER, testSuiteEnvVars, testSuiteVolumes, sandboxRegistryCredentialsId)

            while (true) {
                def state = testSuiteHelper.getContainerStatus("${hostsFile}", TEST_SUITE_CONTAINER)
                if (state.available) {
                    if (state.running) {
                        echo 'Container is still running'
                    } else {
                        if (state.exitCode != 0) {
                            echo "Container has failed with status code: ${state.exitCode}"
                            testSuiteHelper.fetch(hostsFile, '/var/lib/docker/volumes/testsuite/_data/test/logs/common.log', 'test-suite.log', 'tester_nodes', true, true)
                            error "Container has failed with exit code: ${state.exitCode}"
                        } else {
                            echo "Test run finished successfully"
                            break
                        }
                    }
                } else {
                    error 'Container is not available'
                }
                sleep time: 60, unit: 'SECONDS'
            }

            echo "Fetching performance test results file"
            testSuiteHelper.fetch(hostsFile, '/var/lib/docker/volumes/testsuite/_data/test/Data/lastTestResults.html', 'lastTestResults.html', 'tester_nodes', true, true)
            testSuiteHelper.fetch(hostsFile, '/var/lib/docker/volumes/testsuite/_data/test/logs/common.log', 'test-suite.log', 'tester_nodes', true, true)

            resultsPublished = true
        }
    } catch (Exception ex) {
        error 'Error occurred when running pipeline: ' + ex
    } finally {
        stage('Cleanup') {
            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true
            archiveArtifacts artifacts: '**/*.html', allowEmptyArchive: true

            try {
                echo "Stopping $METRICBEATS_CONTAINER"
                testSuiteHelper.stopDockerCompose(hostsFile, DOCKER_COMPOSE_DIRECTORY, 'manager_nodes')
            } catch (Exception e) {
                echo "Error occurred when stopping $METRICBEATS_CONTAINER : $e"
            }

            if (forceDestroyInfrastructure || (destroyInfrastructure && resultsPublished)) {
                echo 'Destroying Infrastructure'
                try {
                    build job: "${removeInfraJob}", propagate: true, wait: true, parameters: [
                            string(name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialsId),
                            string(name: 'CUSTOMER_NAME', value: targetCustomer),
                            string(name: 'ENV_NAME', value: targetEnvironment),
                            booleanParam(name: 'KEEP_ENV_CONFIGS', value: false),
                            booleanParam(name: 'USE_SELECTED_CREDENTIAL', value: true)
                    ]
                } catch (Exception ex) {
                    echo 'Unable to destroy infrastructure: ' + ex
                }
            } else {
                echo "Not destroying infrastructure. Performance test complete: ${resultsPublished}"
            }
        }
    }
}

void deployServices(svnCredentialsId, targetCustomer, targetEnvironment, targetDockerTag, targetVM, registryCredentials) {
    build(job: "${deployServicesJob}", propagate: true, wait: true, parameters: [
            string(name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialsId),
            string(name: 'CUSTOMER_NAME', value: targetCustomer),
            string(name: 'ENV_NAME', value: targetEnvironment),
            string(name: 'STANDARD_DOCKER_VERSION', value: targetDockerTag),
            string(name: 'STANDARD_MYSQL_VERSION', value: 'latest'),
            string(name: 'DOCKER_REGISTRY_CREDENTIALS', value: registryCredentials),
            string(name: 'PUBLIC_IP_ADDRESS', value: targetVM.host)
    ])
}