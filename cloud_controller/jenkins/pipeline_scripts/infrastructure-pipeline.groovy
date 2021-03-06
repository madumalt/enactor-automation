#!groovy

/* 
* Generate terraform modules implementation files(generated.tf, generated-security-groups.tf) and Run terraform apply command
*/

import groovy.json.JsonOutput

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== Jenkins Environment Variables =================== */
def svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
def svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External Parameters =================== */
String svnCredentialID 			 = params.CUSTOMER_CREDENTIAL_ID
String customer             	 = params.CUSTOMER_NAME
String environment          	 = params.ENV_NAME

String clusterProfile 			 = params.CLUSTER_PROFILE
String securityGroups 			 = params.SECURITY_GROUPS
String cloudProviderCredentialID = params.CLOUD_PROVIDER_CREDENTIAL_ID
String region         	   		 = params.REGION


/*===================== SVN Directories =================== */
String customerEnvDir = "customer-env"
String scriptBuilderDir = "terraform/script_generator"

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
			svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${scriptBuilderDir}/", "${scriptBuilderDir}")
			provider = pipelineHelper.getEnv("${customerEnvDir}", 'provider')
		}

		stage("Generating terraform scripts"){
			dir(scriptBuilderDir){
			    def clusterProfileJSON = readJSON text: clusterProfile
				def securityGroupsJSON = readJSON text: securityGroups
				writeJSON(file: "infrastructure.json", json: clusterProfileJSON)
				writeJSON(file: "security-groups.json", json: securityGroupsJSON)
				sh "python --version"
				sh "python terraform-generator.py --input 'infrastructure.json' --output '${workspacePath}/${customerEnvDir}/${infrastructure}/generated.tf' --provider ${provider} "
				sh "python terraform-generator.py --input 'security-groups.json' --output '${workspacePath}//${customerEnvDir}/${infrastructure}/generated-security-groups.tf' --provider ${provider}"
			}
		}

        stage('Create infrastructures') {
            echo "Creating infrastructure with provider: ${provider}"

			sh "cd ${customerEnvDir}/${infrastructure} && terraform init -input=false"

			if( provider == "azure" ) {
				withCredentials([azureServicePrincipal(credentialsId: cloudProviderCredentialID,
													subscriptionIdVariable: 'SUBS_ID',
													clientIdVariable: 'CLIENT_ID',
													clientSecretVariable: 'CLIENT_SECRET',
													tenantIdVariable: 'TENANT_ID')]) {

					def terraformVariables = "-var=\"region=\"$region\"\" -var=\"subscription_id=$SUBS_ID\" -var=\"client_id=$CLIENT_ID\" -var=\"client_secret=$CLIENT_SECRET\" -var=\"tenant_id=$TENANT_ID\""
					applyTerraform(terraformVariables, "${customerEnvDir}/${infrastructure}", svnCredentialID, svnHelper)
				}
			}else if( provider == "aws"){
				withCredentials([usernamePassword(credentialsId: cloudProviderCredentialID, 
												passwordVariable: 'AWS_SECRET_KEY', 
												usernameVariable: 'AWS_ACCESS_KEY')]) {
					def terraformVariables = "-var=\"region=$region\" -var=\"aws_access_key=$AWS_ACCESS_KEY\" -var=\"aws_secret_key=$AWS_SECRET_KEY\""					
					applyTerraform(terraformVariables, "${customerEnvDir}/${infrastructure}", svnCredentialID, svnHelper)
				}
			}else{
				exitWithSuccess = true
				error "Can not run this job when select provider as 'on-prem'"
			}
        }

       

	} finally {
		cleanWs()
	}
}

def applyTerraform(tfVars, tfDir, svnCredentialID, svnHelper){
	dir (tfDir) { 

		sh "terraform plan ${tfVars} -out=tfplan -input=false -no-color"

		applyConfirm()

		try {
			sh "terraform apply -input=false --auto-approve tfplan -no-color"
		} finally { 
			sh 'rm tfplan || exit 0'
			sh "rm -rf .terraform || exit 0"
			svnHelper.add_and_commit(svnCredentialID)
		}
	}
}

def applyConfirm(){
	def userInput = true
	def didTimeout = false
	try {
		timeout(time: 180, unit: 'SECONDS') { // change to a convenient timeout for you
			input message: "Do you want to apply these changes to infrastructure? if not click ABROT before timeout", ok: 'Yes'
		}
		return true
	} catch(err) { // timeout reached or input false
		def user = err.getCauses()[0].getUser()
		if('SYSTEM' == user.toString()) { // SYSTEM means timeout.
			didTimeout = true
			echo "no input was received before timeout. applying changes to infrastructure"
		} else {
			userInput = false
			error "Aborted by: [${user}]"
		}
	}
}