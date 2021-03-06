svnCredentialsId = "72771537-75b6-4838-a194-752f342c71ae"

def svnSelfServiceAppReleaseRepo = env.SELF_SERVICE_SVN_LOCATION_TRUNK

/* ================== EM Information ========================= */
def emServer = [:]
emServer.empHost = params.EMP_HOST
emServer.emsHost = params.EMS_HOST
emServer.empPort = params.EMP_PORT
emServer.emsPort = params.EMS_PORT

/* ============ With Retries =============== */
boolean withRetries = params.WITH_RETRIES

/*================= Data file URL ======================= */
String dataFile = params.FILE_URL

/*============================= Licence.xml Content =================================*/
String fileContent = params.FILE_CONTENT

node("${env.SELF_SERVICE_NODE}") {
    cleanWs()

    stage('Validate Input') {
        if (!dataFile?.trim() && !fileContent?.trim()) {
            error 'Either the data file URL or the file content requires to be provided'
        }
    }

    stage('Run Data Upload') {
        String dataImportDir = 'data-import'
        dir(dataImportDir) {
            // Checkout data-import python script
            String pythonScriptUrl = "${svnSelfServiceAppReleaseRepo}/test_suite/scripts/data_import.py"
            execSVN("export ${pythonScriptUrl}")

            // Upload licence.xml if provided
            if (fileContent?.trim()) {
                String uploadFile = "dataFile.xml"
                echo 'Creating ' + uploadFile
                writeFile file: uploadFile, text: fileContent

                sh 'ls -la'

                echo "Uploading file ${uploadFile}"
                boolean success = uploadDataToEM(emServer, uploadFile, withRetries)
                if (!success) {
                    error "Unable to upload file ${dataFile}"
                }
                echo 'File uploaded successfully'
            }

            if (dataFile?.trim()) {
                echo "Downloading ${dataFile}"
                // Download and upload demo data
                String dataFileName = dataFile.tokenize('/').last()

                // Download demo data zip file
                withCredentials([usernamePassword(credentialsId: svnCredentialsId,
                        passwordVariable: 'SVN_PASSWORD', usernameVariable: 'SVN_USERNAME')]) {
                    sh "wget --user \"${SVN_USERNAME}\" --password \"${SVN_PASSWORD}\" ${dataFile}"
                }

                echo "Uploading ${dataFileName}"
                boolean success = uploadDataToEM(emServer, dataFileName, withRetries)
                if (!success) {
                    error "Failed to upload data file ${dataFileName}"
                }
                echo "${dataFileName} uploaded"
            }
        }
    }
}

/****************** Static functions ****************/
def execSVN(svnCommandLine) {
    withCredentials([usernamePassword(credentialsId: svnCredentialsId,
            passwordVariable: 'SVN_PASSWORD', usernameVariable: 'SVN_USERNAME')]) {
        def svnArgs = "--username \"${SVN_USERNAME}\" --password \"${SVN_PASSWORD}\" --no-auth-cache"
        sh returnStdout: true, script: "svn  ${svnArgs} ${svnCommandLine}"
    }
}

def uploadDataToEM(emServer, String dataFile, boolean withRetries) {
    Integer retries = 1
    if (withRetries) {
        retries = 3
    }

    echo "Uploading data ${dataFile} with retries: ${withRetries}"
    sh 'ls -la'
    while (retries > 0) {
        try {
            echo "Uploading data from file: ${dataFile} to ${emServer.empHost}:${emServer.empPort}"
            sh "python data_import.py ${emServer.empHost} ${emServer.emsHost} ${emServer.empPort} ${emServer.emsPort} ${dataFile}"
            break
        } catch (Exception e) {
            echo 'Error occurred when importing data: ' + e
            retries--
        }
    }

    return retries > 0
}