#!groovy
/* 
* Copy required terraform scripts to the customer's environment folder and commit to the svn
*/

import groovy.json.JsonSlurper

/*===================== Jenkins Environment Variables =================== */
def svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK
def svnCustomersRepo             = env.CUSTOMERS_SVN_LOCATION

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper

/*===================== Environment Information =================== */
String svnCredentialID       = params.CUSTOMER_CREDENTIAL_ID
String customer              = params.CUSTOMER_NAME
String environment           = params.ENV_NAME
String provider              = params.PROVIDER
String osType                = params.OS_TYPE

/*===================== SVN Directories =================== */
String customersDir = "customers"
String infrastructureRootDir = "terraform"

/*===================== Directories Inside Environment =================== */
String infrastructure = 'infrastructure'

/*===================== Internal Parameters =================== */
String customerPathPostfix   = params.CUSTOMER_PATH_POSTFIX
String customerPath          = "${customersDir}/${customer}/${customerPathPostfix}"
String environmentPath       = "${customerPath}/${environment}"
String workspacePath

/*===================== Load helper libraries =================== */
node('master') {
    svnHelper = load("$WORKSPACE@script/helpers/svn_helpers.groovy")
    pipelineHelper = load("$WORKSPACE@script/helpers/pipeline_helpers.groovy")
}

node("${env.SELF_SERVICE_NODE}") {
    try {
        stage('Init & Checkout') {
            cleanWs()
            workspacePath = pwd()
            svnHelper.checkout(svnCredentialID, svnSelfServiceAppReleaseRepo, "${infrastructureRootDir}/", "${infrastructureRootDir}")

            if (svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customer}/${customerPathPostfix}/${environment}")) {
                // if env exists in svn
                println("Environment exists")
                svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}/${customerPathPostfix}/${environment}", "${environmentPath}")
            } else if (svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customer}/${customerPathPostfix}")) {
                // if customer and path exists
                println("Customer and postfixed path exists")
                svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}/${customerPathPostfix}", "${customerPath}", "immediates")
            } else if (svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customer}")) {
                println("Customer path exists")
                svnHelper.checkout(svnCredentialID, svnCustomersRepo, "${customer}", "${customersDir}/${customer}", "immediates")
            } else {
                println("New customer")
                svnHelper.checkout(svnCredentialID, svnCustomersRepo, "", "${customersDir}", "immediates")
            }
        }

        pipelineHelper.stage('Create Environment') {
            sh "mkdir -p ${environmentPath}/${infrastructure}"
            def sourcePath = "${workspacePath}/${infrastructureRootDir}"
            def destinationPath = "${environmentPath}/${infrastructure}"

            if(provider == "aws"){
                sh "cp -a ${sourcePath}/default_scripts/aws/. ${destinationPath}"
                sh "cp -a ${sourcePath}/modules/aws/. ${destinationPath}/modules"
                sh "cp -a ${sourcePath}/templates ${destinationPath}"
            }else if(provider == "azure"){
                sh "cp -a ${sourcePath}/default_scripts/azure/. ${destinationPath}"
                sh "cp -a ${sourcePath}/modules/azure/. ${destinationPath}/modules"
                sh "cp -a ${sourcePath}/templates ${destinationPath}"
            }

            def deploymentParams = pipelineHelper.createDefaultDeploymentParamsJson("${environmentPath}/deployment-params.json")
            def environmentParams = deploymentParams['environment-params']
            def svnRevision = svnHelper.execSVN("info --show-item revision ${infrastructureRootDir}/", svnCredentialID)
            environmentParams['os'] = osType
            environmentParams['provider'] = provider
            environmentParams['created_with_release'] = svnSelfServiceAppReleaseRepo + "/?p=" + svnRevision.trim()
            writeJSON(file: "${environmentPath}/deployment-params.json", json: deploymentParams)

            if (svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customer}/${customerPathPostfix}/${environment}")) {
                // if env exists in svn
                println("================================= env exists")
                svnHelper.add_and_commit(svnCredentialID, environmentPath, true)
            } else if (svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customer}/${customerPathPostfix}")) {
                // if customer and path exists
                println("================================= customer and path exists")
                svnHelper.add_and_commit(svnCredentialID, "${customerPath}", true)
            } else if (svnHelper.isSvnPathExists(svnCredentialID, svnCustomersRepo, "${customer}")) {
                println("================================= customer post fixed path exists")
                svnHelper.add_and_commit(svnCredentialID, "${customersDir}/${customer}", true)
            } else {
                println("================================= new customer")
                svnHelper.add_and_commit(svnCredentialID, "${customersDir}", true)
            }
        }
    } finally {
        cleanWs()
    }
}