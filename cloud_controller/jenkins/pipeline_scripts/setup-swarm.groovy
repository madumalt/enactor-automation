#!groovy

/* Invoke  ansible playbooks to initialize swarm clusture */

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
String svnCredentialID 	= params.CUSTOMER_CREDENTIAL_ID
String customer       	= params.CUSTOMER_NAME
String environment    	= params.ENV_NAME
String os

String workspacePath

/*===================== Jenkins Pipeline =================== */
node('master') {
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
	svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
}

boolean exitWithSuccess = false
node("${env.SELF_SERVICE_NODE}") {

	try {
		stage('Init & Checkout') {
			cleanWs()
			workspacePath = pwd()
			parallel(
				checkout_env_configs: {
                    svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}/${environment}", "${customerEnvDir}")      							
				},
                checkout_playbooks: {
                    svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${playbooksDir}/", "${playbooksDir}")        							
				}
			)
			
            
			os = pipelineHelper.getEnv("${customerEnvDir}", 'os')
		}

		stage('Invoke playbook') { 
			def hostsFile = "${workspacePath}/${customerEnvDir}/${infrastructure}/infrastructure-details.json"
			dir ('ansible/enactor-automation') {
				if ( os == "windows") {
					sh "ansible-playbook -i ${hostsFile} windows-swarm-cluster.yml"
				}
				else if( os == "linux"){
					sh "ansible-playbook -i ${hostsFile} linux-swarm-cluster.yml"
				}
			}
		}
	} finally {
		cleanWs()
	}
}
