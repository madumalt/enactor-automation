import groovy.json.JsonSlurper

/*
 * Script with helper methods for Performance Test Suite. Some of these methods make use of
 * ansible to achieve intended goal.
 */

/**
 * Run a docker container
 * @param hostsFile
 * @param imageName
 * @param containerName
 * @param environment
 * @param volumes
 * @return
 */
def runDockerContainer(hostsFile, imageName, containerName, environment, volumes, sandboxRegistryCredentials = 'f19e1121-4cea-4129-af4f-2eae655b13f9') {
    String envVars = ""
    environment.each { key, value ->
        envVars += "-e $key=$value "
    }

    volumeStr = '['
    volumes.eachWithIndex { key, value, index ->
        volumeStr += "\"${key}:${value}\""
        if (index != volumes.size() - 1) {
            volumeStr += ','
        }
    }
    volumeStr += "]"
    envVars += "-e '{\"volumes\":$volumeStr}'"
    echo "Environment Vars: $envVars"

    dir('ansible/test-suite') {
        // Run container
        withCredentials([usernamePassword(credentialsId: sandboxRegistryCredentials, passwordVariable: 'password', usernameVariable: 'username')]) {
            sh """ansible-playbook -i ${hostsFile} \
                        -e host_group="tester_nodes" \
                        -e docker_registry=\"enactorsandbox.azurecr.io\" \
                        -e docker_registry_username=${username} \
                        -e docker_registry_password=\"${password}\" \
                        -e docker_image=${imageName} \
                        -e container_name=${containerName} \
                        ${envVars} \
                        linux-run-test-suite-container.yml"""
        }
    }
}

/**
 * Run a specified service in a docker-compose.yml
 * @param hostsFile
 * @param composeDirectory
 * @param serviceName
 * @param environment
 * @return
 */
def runDockerCompose(hostsFile, composeDirectory, serviceName, environment) {
    String envVars = '['
    environment.eachWithIndex { key, value, index ->
        envVars += "\"$key=$value\""
        if (index != environment.size() - 1) {
            envVars += ','
        }
    }
    envVars += ']'

    echo "$envVars"

    dir('ansible/test-suite') {
        // Run container
        sh """ansible-playbook -i ${hostsFile} \
                        -e host_group="manager_nodes" \
                        -e docker_compose_directory="${composeDirectory}" \
                        -e service_name="${serviceName}" \
                        -e '{"environment_variables":$envVars}' \
                        linux-run-docker-compose.yml"""
    }
}

def stopDockerCompose(hostsFile, composeDirectory, hostGroup) {
    runShellCommand(hostsFile, composeDirectory, "docker-compose down", hostGroup, false)
}

def restartEMServices(hostsFile) {
    restartDockerService(hostsFile, '.', 'em-processing', 'manager_nodes')
    restartDockerService(hostsFile, '.', 'em-application', 'manager_nodes')
    restartDockerService(hostsFile, '.', 'em-services', 'manager_nodes')
}

def restartDockerService(hostsFile, directory, serviceName, hostGroup) {
    runShellCommand(hostsFile, directory, "docker service update dockerstack_${serviceName} --force", hostGroup)
}

/**
 * Wait for WebCore to be up. If EMP server is accessible but not the WebCore, we restart the EMP service.
 * @param emServer
 * @param hostsFile
 * @return
 */
def waitForWebCoreStartup(emServer, hostsFile) {
    while (!isEMPUp(emServer.empHost, emServer.empPort) || !isWebCoreUp(emServer.empHost, emServer.empPort)) {
        if (isEMPUp(emServer.empHost, emServer.empPort) && !isWebCoreUp(emServer.empHost, emServer.empPort)) {
            echo 'Identified EMP Server being up, but WebCore failing. Restarting EMP'
            restartDockerService(hostsFile, '.', 'em-processing', 'manager_nodes')
        }

        sleep time: 60, unit: 'SECONDS'
    }

    echo "WebCore startup complete: ${emServer.empHost}:${emServer.empPort}"
}

def waitForStartup(emServer, hostsFile) {
    waitForWebCoreStartup(emServer, hostsFile)

    while (!isServicesUp(emServer)) {
        sleep time: 60, unit: 'SECONDS'
    }

    echo "Server startup complete: ${emServer.emaHost}:${emServer.emaPort}"
}

boolean isServicesUp(emServer) {
    return isWebCoreUp(emServer.empHost, emServer.empPort) &&
            isWebMaintenanceUp(emServer.emaHost, emServer.emaPort) &&
            isEMSUp(emServer.emsHost, emServer.emsPort)
}

boolean isWebCoreUp(host, port) {
    return isUrlUp("http://${host}:${port}/WebCore")
}

boolean isEMPUp(host, port) {
    return isUrlUp("http://${host}:${port}")
}

boolean isWebMaintenanceUp(host, port) {
    return isUrlUp("http://${host}:${port}/WebMaintenance")
}

boolean isEMSUp(host, port) {
    return isUrlUp("http://${host}:${port}/")
}

boolean isUrlUp(url) {
    try {
        sh "wget --timeout=60 --tries=1 ${url}"
        return true
    } catch (Exception e) {
        echo "Unable to connect to: ${url} - ${e}"
        return false
    }
}

/**
 * Get the container status. Optionally get logs of that container if running.
 * @param hostsFile
 * @param containerName
 * @param withLogs
 * @param logFile
 * @return
 */
def getContainerStatus(String hostsFile, String containerName, boolean withLogs = false, String logFile = 'container.log') {
    String responseFile = 'response.json'
    def state = [:]
    dir('ansible/test-suite') {
        sh """ansible-playbook -i ${hostsFile} \
                -e host_group="tester_nodes" \
                -e fetch_container_logs=${withLogs ? 'yes' : 'no'} \
                -e container_status_file=${responseFile} \
                -e container_logs_file=${logFile} \
                -e container_name=${containerName} \
                linux-check-container-running.yml"""

        def content = readFile responseFile
        if (content?.trim()) {
            def slurper = new JsonSlurper()
            def result = slurper.parseText(content)

            state.available = result.size() > 0
            if (state.available) {
                result = result[0]
                state.running = result.State.Running
                state.status = result.State.Status
                state.exitCode = result.State.ExitCode
                state.error = result.State.Error

                if (withLogs) {
                    state.logFile = "${pwd()}/${logFile}"
                }
            }
        } else {
            state.available = false
        }
    }

    echo "${state}"
    return state
}

def fetchPerformanceLogs(String hostsFile) {
    fetchFolderAsZip(hostsFile, '/var/lib/docker/volumes/enactor-data-emp-home/_data/Performance', 'PerformanceLogs.zip', 'manager_nodes', true)
}

/**
 * Uploads performance logs to testsuite containers docker volume
 * @param hostsFile
 * @return
 */
def uploadPerformanceLogs(String hostsFile) {
    copyAndExtractZip(hostsFile, "PerformanceLogs.zip", "/var/lib/docker/volumes/testsuite/_data/test/Data/PerformanceLogs", "tester_nodes", true)
}

/**
 * Copy a local folder to a remote host
 * @param hostsFile
 * @param source
 * @param destination
 * @param hostGroup
 * @return
 */
def copyFolder(String hostsFile, String source, String destination, String hostGroup, boolean become = false) {
    dir('ansible/test-suite') {
        sh "ansible-playbook -i ${hostsFile} " +
                "-e host_group=${hostGroup} " +
                "-e become=${become ? 'yes' : 'no'} " +
                "-e source_directory=${source} " +
                "-e destination_directory=${destination} " +
                "linux-copy-folder.yml"
    }
}

/**
 * Copies a ZIP file and extract it to the provided location
 * @param hostsFile
 * @param source
 * @param destination
 * @param hostGroup
 * @param become
 * @return
 */
def copyAndExtractZip(String hostsFile, String source, String destination, String hostGroup, boolean become = false) {
    dir('ansible/test-suite') {
        sh "ansible-playbook -i ${hostsFile} " +
                "-e host_group=${hostGroup} " +
                "-e become=${become ? 'yes' : 'no'} " +
                "-e zip_file_location=${source} " +
                "-e destination_directory=${destination} " +
                "linux-copy-zip.yml"
    }
}

def fetch(String hostsFile, String source, String destination, String hostGroup, boolean flat = true, boolean become = false) {
    dir('ansible/test-suite') {
        sh "ansible-playbook -i ${hostsFile} " +
                "-e host_group=${hostGroup} " +
                "-e source_location=${source} " +
                "-e with_flat=${flat ? 'yes' : 'no'} " +
                "-e become_root=${become ? 'yes' : 'no'} " +
                "-e destination_location=${destination} " +
                "linux-fetch.yml"
    }
}

/**
 * Use ansible to zip a remote folder and download the zipped file.
 * @param hostsFile
 * @param source
 * @param destination
 * @param become
 * @param hostGroup
 * @return
 */
def fetchFolderAsZip(String hostsFile, String source, String destination, String hostGroup, boolean become) {
    dir('ansible/test-suite') {
        sh "ansible-playbook -i ${hostsFile} " +
                "-e host_group=${hostGroup} " +
                "-e source_directory=${source} " +
                "-e destination_file_location=${destination} " +
                "-e with_flat=yes " +
                "-e become=${become ? 'yes' : 'no'} " +
                "linux-zip-and-fetch-folder.yml"
    }
}

def runShellCommand(hostsFile, directory, command, hostGroup, boolean showOutput = true) {
    dir('ansible/test-suite') {
        // Run container
        sh """ansible-playbook -i ${hostsFile} \
                        -e host_group=${hostGroup} \
                        -e command_directory="${directory}" \
                        -e \"remote_command=\'${command}\'\" \
                        -e show_output="${showOutput ? 'yes' : 'no'}" \
                        linux-run-command.yml"""
    }
}

/**
 * Get given type of node's information from ansible inventory
 * @param inventory
 * @param customer
 * @param environment
 * @param nodeType
 * @return
 */
def getNodeFromInventory(inventory, customer, environment, nodeType) {
    def node = [:]
    String nodeName = nodeType.split('_')[0]

    inventory['all']['children'][nodeType]['hosts'].each { host, info ->
        String targetNodeName = "${customer}-${environment}-${nodeName}"
        if (host == targetNodeName) {
            echo "Found node of type ${nodeType}-${nodeName}: ${host}"
            node.name = host
            node.host = info.ansible_host
            node.user = info.ansible_user
            node.password = info.ansible_password
            echo "Found leader: ${host} -> ${node.host}"
        }
    }

    return node
}

/**
 * Create an object containing host names and ports of EMA, EMP, and EMS
 * @param targetVM
 * @param emaPort
 * @param empPort
 * @param emsPort
 * @param queue
 * @return
 */
def getEMServer(targetVM, emaPort, empPort, emsPort, queue) {
    def emServer = [:]
    emServer.emaHost = targetVM.host
    emServer.empHost = targetVM.host
    emServer.emsHost = targetVM.host
    emServer.emaPort = emaPort
    emServer.empPort = empPort
    emServer.emsPort = emsPort
    emServer.queueName = queue

    return emServer
}

/**
 * Get EM's Database information. Depends on the database type.
 * @param dbType
 * @param host
 * @param dbName
 * @return
 */
def getEMDatabase(dbType, host, dbName) {
    def emDatabase = [:]
    emDatabase.host = host
    emDatabase.dbName = dbName

    if (dbType == "MySQL") {
        emDatabase.driver = 'com.mysql.jdbc.Driver'
        emDatabase.user = 'root'
        emDatabase.password = 'enactor'
        emDatabase.port = 3326
        emDatabase.isMSSQL = false
        emDatabase.isMySQL = true
        emDatabase.url = "jdbc:mysql://${host}:${emDatabase.port}/${dbName}"
    } else if (dbType == "MSSQL") {
        emDatabase.driver = 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
        emDatabase.user = 'sa'
        emDatabase.password = 'Enactor123'
        emDatabase.port = 2433
        emDatabase.isMSSQL = true
        emDatabase.isMySQL = false
        emDatabase.url = "jdbc:sqlserver://${host}:${emDatabase.port};DatabaseName=${dbName}"
    } else {
        error "Unknown DB type ${dbType}"
    }

    return emDatabase
}

def setupMSSQL(hostsFile, workspaceMSSQLDir, remoteMSSQLDir, emDatabase) {
    copyFolder(hostsFile, "${workspaceMSSQLDir}/", "./${remoteMSSQLDir}", "manager_nodes")
    runShellCommand(hostsFile, "./${remoteMSSQLDir}", "docker-compose up -d", "manager_nodes")
    // MSSQL image should be downloaded and run. Wait some time
    sleep time: 180, unit: 'SECONDS'

    String createDBCommand = """docker exec -it testsuitemssql /opt/mssql-tools/bin/sqlcmd -S localhost -U '${emDatabase.user}' -P '${emDatabase.password}' -Q \\\"CREATE DATABASE ${emDatabase.dbName}\\\" """
    runShellCommand(hostsFile, "./${remoteMSSQLDir}", createDBCommand, "manager_nodes")
    sleep time: 30, unit: 'SECONDS'
    runShellCommand(hostsFile, "./${remoteMSSQLDir}", "docker-compose down", "manager_nodes")
    sleep time: 30, unit: 'SECONDS'
    runShellCommand(hostsFile, "./${remoteMSSQLDir}", "docker-compose up -d", "manager_nodes")
    sleep time: 30, unit: 'SECONDS'
}

def getFileSystemItemList(filePath) {
    return sh(script: "ls -1 $filePath", returnStdout: true).split()
}

def updateEnvDBVariables(emDatabase, envDirectory) {
    def envFiles = getFileSystemItemList(envDirectory)
    dir(envDirectory) {
        envFiles.each {
            String filePath = "${envDirectory}/${it}"
            String fileContent = readFile filePath
            def lines = fileContent.split('\n')
            def newLines = []
            lines.each {
                if (it?.trim()) {
                    String key = it.split('=')[0]
                    if (key) {
                        switch (key) {
                            case 'ENACTOR_DB_PASS':
                                newLines += "${key}=${emDatabase.password}"
                                break
                            case 'ENACTOR_DB_USER':
                                newLines += "${key}=${emDatabase.user}"
                                break
                            case 'ENACTOR_DB_DRIVERCLASSNAME':
                                newLines += "${key}=${emDatabase.driver}"
                                break
                            case 'ENACTOR_DB_JDBC_URL':
                                newLines += "${key}=${emDatabase.url}"
                                break
                            default:
                                newLines += it
                        }
                    }
                } else {
                    newLines += it
                }
            }

            fileContent = newLines.join('\n')
            writeFile(file: filePath, text: fileContent)
        }
    }
}

def addEnvVariableToFile(envFile, varName, varValue) {
    String fileContent = readFile envFile
    fileContent += '\n'
    fileContent += "${varName}=${varValue}"
    writeFile(file: envFile, text: fileContent)
}

return this