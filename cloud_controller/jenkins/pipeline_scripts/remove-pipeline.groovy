#!groovy
/* 
* Prompt user to provide infrastructure destroy parameters
* If KEEP_ENV_CONFIGS is true, do not delete configurations, but destoy the infrastructure
*/

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper

/*===================== Jenkins Environment Variables =================== */
String svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
String svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== SVN Directories =================== */
String customerEnvDir = "customer-env"

/*===================== Directories Inside Environment =================== */
String infrastructure = 'infrastructure'
String customerPath

/*===================== Job Parameters =================== */
String svnCredentialID = params.CUSTOMER_CREDENTIAL_ID
String customerName = params.CUSTOMER_NAME
String enviromentName = params.ENV_NAME
boolean useSelectedCredential = params.USE_SELECTED_CREDENTIAL
boolean keepEnvironmentConfigs = params.KEEP_ENV_CONFIGS
String provider

/*===================== Internal Parameters =================== */
String workspacePath

/*===================== Load helper libraries =================== */
node('master') {
    svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
}

node("${env.SELF_SERVICE_NODE}") {
    try {
        
        stage('Init') {
            cleanWs()
            workspacePath = pwd()
            customerPath = customerName + "/" + params.CUSTOMER_PATH_POSTFIX
            svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customerPath}/${enviromentName}", "${customerEnvDir}")
			provider = pipelineHelper.getEnv("${customerEnvDir}", 'provider')
        }

        pipelineHelper.stage('Remove infrastructure and environment', (provider != "on-prem")) {
            def deploymentParams = readJSON(file: "${customerEnvDir}/deployment-params.json")
            def infrastructureParams = deploymentParams['infrastructure-params']

            if(!useSelectedCredential){
                def infrastructureInputParams = [
                        [
                            $class: 'CredentialsParameterDefinition',
                            name: 'CLOUD_PROVIDER_CREDENTIAL_ID',
                            defaultValue: infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] ? infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] : "",
                            description: 'Select cloud provider jenkins credentials'
                        ]
                    ]

                def cloudProviderCredentialID = input(message: 'Provide the parameters for deployement infrastructres.', parameters: infrastructureInputParams)
                infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] = cloudProviderCredentialID
                
                writeJSON(file: "${customerEnvDir}/deployment-params.json", json: deploymentParams)
                svnHelper.add_and_commit(svnCredentialID, customerEnvDir, true)
            }

            def buildParameters = [
                string( name: 'CUSTOMER_CREDENTIAL_ID', value: svnCredentialID),
                string( name: 'CUSTOMER_NAME',   value: customerPath ),
                string( name: 'ENV_NAME',        value: enviromentName ),
                string( name: 'REGION',          value: infrastructureParams['REGION'] ?: ''),
                booleanParam(name: 'KEEP_ENV_CONFIGS', value: keepEnvironmentConfigs),
                string( name: 'CLOUD_PROVIDER_CREDENTIAL_ID', value: infrastructureParams['CLOUD_PROVIDER_CREDENTIAL_ID'] ?: '')
            ]
            
            build(job: 'deployment-tasks/deregister-environment', propagate: true, wait: true, parameters: buildParameters)
        }

    } finally {
        cleanWs()
    }
}