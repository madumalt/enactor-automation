#!groovy
// Invoke linux/windows ansible playbooks to install docker and python modules 
/*===================== Jenkins Environment Variables =================== */
String svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
String svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== SVN Directories =================== */
String playbooksDir = "ansible"
String customerEnvDir = "customer-env"

/*===================== Directories Inside Environment =================== */
String infrastructure = 'infrastructure'

/*===================== Environment Information =================== */
String svnCredentialID = params.CUSTOMER_CREDENTIAL_ID
String customer       = params.CUSTOMER_NAME
String environment    = params.ENV_NAME
String nodeConfigMode = (params.NODE_CONFIG_MODE == null) ? 'normal' : params.NODE_CONFIG_MODE
String os

String workspacePath

/*===================== Jenkins Pipeline =================== */
node('master') {
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
	svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
}

/* 
* Invoke ansible playbook to install required software coponents
*/
node("${env.SELF_SERVICE_NODE}") {
	try {
		stage('Init & Checkout') {
			cleanWs()
			workspacePath = pwd()
			parallel(
                checkout_configs: {
					svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}/${environment}", "${customerEnvDir}")
				},
				checkout_playbook: {
            		svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${playbooksDir}/", "${playbooksDir}")					
				}
			)
			os = pipelineHelper.getEnv("${customerEnvDir}", 'os')
		}

		stage('Invoke playbook') { 
			def hostsFile = "${workspacePath}/${customerEnvDir}/${infrastructure}/infrastructure-details.json";
			dir ("${playbooksDir}/enactor-automation") {
				if ( os == "windows") {
					sh "ansible-playbook -i ${hostsFile} windows-docker.yml \
					-e node_config_mode=${nodeConfigMode}"
				}
				else if( os == "linux"){
					sh "ansible-playbook -i ${hostsFile} linux-docker.yml \
					-e node_config_mode=${nodeConfigMode}"
				}
			}
		}
	} finally {
		cleanWs()
	}
}