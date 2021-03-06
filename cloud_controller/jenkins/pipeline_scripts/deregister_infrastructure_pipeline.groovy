#!groovy
import groovy.json.JsonOutput
/* 
* Run terraform destroy to remove infrastructure
*/

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== Jenkins Environment Variables =================== */
def svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External Parameters =================== */
String svnCredentialID 			 = params.CUSTOMER_CREDENTIAL_ID
String customer             	 = params.CUSTOMER_NAME
String environment          	 = params.ENV_NAME
boolean keepEnvironmentConfigs   = params.KEEP_ENV_CONFIGS
String cloudProviderCredentialID = params.CLOUD_PROVIDER_CREDENTIAL_ID
String region         	   		 = params.REGION

/*===================== SVN Directories =================== */
String customerEnvDir = "customer-env"

/*===================== Directories Inside Environment =================== */
String infrastructure = 'infrastructure'

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
            svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}/${environment}", "${customerEnvDir}")
			provider = pipelineHelper.getEnv("${customerEnvDir}", 'provider')
		}

        stage('Remove infrastructures') {

			sh "cd ${customerEnvDir}/${infrastructure} && terraform init -input=false"
			if( provider == "azure" ) {
				withCredentials([azureServicePrincipal(credentialsId: cloudProviderCredentialID,
													subscriptionIdVariable: 'SUBS_ID',
													clientIdVariable: 'CLIENT_ID',
													clientSecretVariable: 'CLIENT_SECRET',
													tenantIdVariable: 'TENANT_ID')]) {

					def terraformVariables = "-var=\"region=\"$region\"\" -var=\"subscription_id=$SUBS_ID\" -var=\"client_id=$CLIENT_ID\" -var=\"client_secret=$CLIENT_SECRET\" -var=\"tenant_id=$TENANT_ID\""
					dir ("${customerEnvDir}/${infrastructure}") { 
						try {
							sh "terraform destroy -input=false --auto-approve ${terraformVariables}"
						} finally { 
							sh "rm -rf .terraform || exit 0"
							svnHelper.add_and_commit(svnCredentialID, )
						}
					}
				}
				
			}else if( provider == "aws"){
				withCredentials([usernamePassword(credentialsId: cloudProviderCredentialID, 
												passwordVariable: 'AWS_SECRET_KEY', 
												usernameVariable: 'AWS_ACCESS_KEY')]) {
					def terraformVariables = "-var=\"region=$region\" -var=\"aws_access_key=$AWS_ACCESS_KEY\" -var=\"aws_secret_key=$AWS_SECRET_KEY\""					
					dir ("${customerEnvDir}/${infrastructure}") { 
						try {
							sh "terraform destroy -input=false --auto-approve ${terraformVariables}"
						} finally { 
							sh "rm -rf .terraform || exit 0"
							svnHelper.add_and_commit(svnCredentialID, )
						}
					}
				}
			}
        }

        stage('Remove environment configs') {
            if(keepEnvironmentConfigs){
                println("Skiped without removing environment configs")
            }else{
                svnHelper.remove_and_commit(svnCredentialID, svnCustomersRepo, "${customer}/${environment}")
            }
        }
        
       

	}finally{
		cleanWs()
	}
}