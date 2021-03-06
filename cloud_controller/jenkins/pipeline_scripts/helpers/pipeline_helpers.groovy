@Grab(group='com.fasterxml.jackson.core', module='jackson-databind', version='2.9.3')
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*

mavenVersion            = 'Current Apache Maven'

/*=========================================================================================*/
/*=                                    Pipeline Helpers                                   =*/
/*=========================================================================================*/

def stage(name, execute, block) {
    return stage(name, execute ? block : {
        echo "Skipping stage ${name}."
    })
}

def getEnv(path,key){
    def deploymentParams = readJSON(file: "${path}/deployment-params.json")
    def environmentParams = deploymentParams['environment-params']
    return environmentParams[key].trim()
}

def execMaven(mvnCommandLine) {
    try {
        withMaven( // for jenkins2 install maven on demand in the running node
            maven: mavenVersion,
            mavenOpts: '',
            mavenSettingsConfig: '',
            mavenSettingsFilePath: '') {
                sh "mvn  ${mvnCommandLine}";
            }
    } catch (NoSuchMethodError error) {
        sh "mvn  ${mvnCommandLine}";  // self contained jenkins with pre installed maven
    }
}

def getFileSystemItemList(filePath){
    return sh(script: "ls -1 $filePath", returnStdout: true).split()
}

def listFileSystemItems(filePath){
    def foundFiles = getFileSystemItemList(filePath)
    String choiceList =  foundFiles.join("\n")
    return choiceList
}

def dbConfigEnvs(username, password, jdbc_driver, schema_name, jdbc_url){
    def db_configs = """
ENACTOR_DB_USER=${username}
ENACTOR_DB_PASS=${password}
ENACTOR_DB_DRIVERCLASSNAME=${jdbc_driver}
ENACTOR_COMMON_DATABASESCHEMA=${schema_name}
ENACTOR_DB_JDBC_URL=${jdbc_url}
"""
    return db_configs
}

String getManagerIP(jsonText){
    String managerIP
    def slurper = new JsonSlurper()
    def jsonObject = slurper.parseText(jsonText)
    jsonObject.all.children.manager_nodes.hosts.each { key, value -> managerIP = value.ansible_host }
    return managerIP
}

def createDefaultDeploymentParamsJson(filePath) {
    def defaultDeploymentParams = readJSON(text: '{"environment-params":{},"infrastructure-params":{"INFRASTRUCTURE":{}}, "enactor-suite-params":{"service-list":[], "service-feature-map":{}}}')
    writeJSON(file: filePath, json: defaultDeploymentParams)
    return defaultDeploymentParams
}


def listMovetoTop(List list,String item=null){
    if(item){
        list.removeAll{ it == item}
        list.add(0, item);
    }
    return list
}

def generateSecurityGroupsJson(serviceFilesList, servicePorts, machinesJSON, provider){
    def parser = new JsonSlurper()
    def defaultPorts =  [[port:22], [port:3389],[port:3389,protocol:'udp'], [port:5985], [port:5986]]
    def securityGroups = [:]

    def swarmSecurityGroup = [group_identifier: "swarm", rules: []]
    def serviceList = serviceFilesList.collect { it.minus("-stack.yml") }
    def rulesList = serviceList.collect { servicePorts.get(it)?  servicePorts.get(it):[port: "0"] }.flatten()
    
    if(provider == "aws"){
        swarmSecurityGroup['rules'] = rulesList.unique()
        securityGroups = machinesJSON.collect {
            [group_identifier: it['host_label'], rules: defaultPorts]
        }
        securityGroups << swarmSecurityGroup
    }else if(provider == "azure"){
        securityGroups = machinesJSON.collect {
            if(it['host_category'] == "manager" || it['host_category'] == "worker"){
                defaultPorts = defaultPorts.plus(rulesList.unique())
            }
            return [group_identifier: it['host_label'], rules: defaultPorts ] 
        }
    }
    
    securityGroupsJSON = [security_groups:securityGroups]
    return securityGroupsJSON
}

// groovy json parser doesn't validate jsons properly,
// https://stackoverflow.com/questions/48469200/groovy-validate-json-string
def isValidJSON(String json,boolean showErrorMsg=true){
    def isValid = false
    ObjectMapper mapper = new ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
    try {
        mapper.readValue(json, Map)
        isValid = true
    } catch (com.fasterxml.jackson.core.JsonParseException ex) {
        if(showErrorMsg){
            println("==============================Invalid JSON input==============================")
            println(ex.getMessage())
            println("==============================================================================")
        }
        isValid = false
    } finally {
        return isValid
    }
}

return this