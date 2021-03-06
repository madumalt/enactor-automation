sandboxRegistryCredentialsId = 'f19e1121-4cea-4129-af4f-2eae655b13f9'

/* ================= Helpers ================= */
def testSuiteHelper

/*============= Constants ================*/
def EMA_PORT = 39830
def EMP_PORT = 39832
def EMS_PORT = 39833
def DB_PORT = 3326
def DB_DATABASE = 'enactorem'

def svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
def svnCustomersRepo = env.CUSTOMERS_SVN_LOCATION

String TEST_SUITE_DOCKER_IMAGE = 'enactorsandbox.azurecr.io/emperformancetestsuite/test-suite:release-0.6'
String TEST_SUITE_CONTAINER = 'test-suite'

String simulationDuration = params.SIMULATION_DURATION
String simulationNoOfDevices = params.SIMULATION_NO_OF_DEVICES
String simulationNoOfThreads = params.SIMULATION_NO_OF_THREADS
String simulationTransactionType = params.SIMULATION_TRANSACTION_TYPE
String simulationMaxQueueLength = params.SIMULATION_MAX_QUEUE_LENGTH
String simulationInterArrivalTime = params.SIMULATION_INTER_ARRIVAL_TIME
String simulationServiceTime = params.SIMULATION_SERVICE_TIME
String simulationServiceTimeStd = params.SIMULATION_SERVICE_TIME_STD
String simulationMaxSaleItems = params.SIMULATION_MAX_SALE_ITEMS

def testVM = [:]
testVM.name = 'TestVM'
testVM.host = params.TEST_VM_HOST
testVM.user = params.TEST_VM_USER
testVM.password = params.TEST_VM_PASSWORD
testVM.allowAnyHosts = true
testVM.timeoutSec = 60

def esServer = [:]
esServer.host = params.ES_HOST
esServer.port = params.ES_PORT
esServer.scheme = params.ES_SCHEME

def kibanaServer = [:]
kibanaServer.host = params.KIBANA_HOST
kibanaServer.port = params.KIBANA_PORT

def emServer = [:]
emServer.empHost = params.EMP_HOST
emServer.empPort = params.EMP_PORT ? params.EMP_PORT : EMP_PORT
emServer.queueName = params.SERVER_QUEUE ? params.SERVER_QUEUE : 'ServerTransactions'

/*===================== Infrastructure Information =================== */
def targetCustomer = "${params.CUSTOMER_NAME}"
def targetProvider = "${params.PROVIDER}"
def targetEnvironment = "${params.ENV_NAME}"
def targetReleaseVersion = "${params.RELEASE_VERSION}"

/* ======================= JDBC ========================= */
def emDatabase = [:]
emDatabase.driver = params.EM_JDBC_DRIVER
emDatabase.url = params.EM_JDBC_URL
emDatabase.user = params.EM_JDBC_USER
emDatabase.password = params.EM_JDBC_PASSWORD


/*====== Skip Message Generation? =======*/
boolean skipMessageGeneration = params.SKIP_MESSAGE_GENERATION

String simulationName = "${targetCustomer}_${targetEnvironment}_${targetReleaseVersion}"

String logsFile = "test-suite.log"

/*===================== Jenkins Pipeline =================== */
node('master') {
    svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
    testSuiteHelper = load("$WORKSPACE@script/helpers/test_suite_helpers.groovy")
}

node("${env.SELF_SERVICE_NODE}") {

    cleanWs()

    try {
        String hostsFile = "${WORKSPACE}/hosts.yml"
        stage("Checkout") {
            svnHelper.checkout("72771537-75b6-4838-a194-752f342c71ae", svnSelfServiceAppReleaseRepo, "ansible/", "ansible")

            // Write ansible_hosts.yml file
            def ansibleHost = [
                    'TestVM': [
                            "ansible_host"              : "${testVM.host}",
                            "ansible_password"          : "${testVM.password}",
                            "ansible_port"              : "22",
                            "ansible_python_interpreter": "/usr/bin/python3",
                            "ansible_user"              : "${testVM.user}"
                    ]]
            def hosts = ['all': ['children': ['tester_nodes': ['hosts': ansibleHost]]]]

            writeYaml file: hostsFile, data: hosts
        }

        stage("Run Performance Test") {
            def envVariables = [
                    docker_image          : TEST_SUITE_DOCKER_IMAGE,
                    container_name        : TEST_SUITE_CONTAINER,
                    simulation_name       : simulationName,
                    duration              : simulationDuration,
                    no_of_devices         : simulationNoOfDevices,
                    no_of_threads         : simulationNoOfThreads,
                    transaction_type      : simulationTransactionType,
                    max_queue_length      : simulationMaxQueueLength,
                    inter_arrival_time    : simulationInterArrivalTime,
                    service_time          : simulationServiceTime,
                    service_time_std      : simulationServiceTimeStd,
                    max_sale_items        : simulationMaxSaleItems,
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
                    jdbc_url              : emDatabase.url,
                    jdbc_user             : emDatabase.user,
                    jdbc_password         : emDatabase.password,
                    operation             : 'run'
            ]

            def volumes = [
                    'testsuite': '/enactor/app/home'
            ]

            testSuiteHelper.runDockerContainer("${hostsFile}", TEST_SUITE_DOCKER_IMAGE, TEST_SUITE_CONTAINER, envVariables, volumes)

            try {
                while (true) {
                    def state = testSuiteHelper.getContainerStatus("${hostsFile}", TEST_SUITE_CONTAINER, true, logsFile)
                    if (state.available) {
                        if (state.running) {
                            echo 'Container is still running'
                        } else if (state.exitCode != 0) {
                            error "Container has failed with exit code: ${state.exitCode}"
                        } else {
                            echo "Test run finished successfully"

                            def content = readFile state.logFile
                            echo "${content}"

                            break
                        }
                    } else {
                        error 'Container is not available'

                    }
                    sleep time: 60, unit: 'SECONDS'
                }
            } catch (Exception ex) {
                error "Test Suite execution failed: ${ex}"
            }
        }

    } finally {
        try {
        } catch (Exception e) {
            echo "${e}"
        }
    }
}