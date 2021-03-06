import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*

// Checkout from svn. Local directory tree will be created if does not exist.
def checkout(svnCredentialsId, svnRoot, relativeRemotePath, localPath) { 
    // Create local folder before checkout.
    sh "mkdir -p $localPath"
    execSVN("co ${svnRoot}/${relativeRemotePath} ${localPath}", svnCredentialsId)
}

// Checkout from svn. Local directory tree will be created if does not exist.
def checkout(svnCredentialsId, svnRoot, relativeRemotePath, localPath, depth) { 
    // Create local folder before checkout.
    sh "mkdir -p $localPath"
    execSVN("co ${svnRoot}/${relativeRemotePath} ${localPath} --depth ${depth}", svnCredentialsId)
}

// List folders in give svn path.
def getSvnFolderList(svnCredentialsId, svnRoot, relativeRemotePath) {
    try{
        def svnlsOutput = execSVN("ls ${svnRoot}/${relativeRemotePath}", svnCredentialsId)
        return svnlsOutput.split("\n").collect{ it.minus("/")};
    }catch(Exception ex){
        println(ex.getMessage())
        return [];
    }
}

// Add files in a given path to svn and commit.
def add_and_commit(svnCredentialsId, commitPath=".", force=true, message="#INF-1476 - pipeline commit") { 
    if (force) {
        execSVN("add --force $commitPath", svnCredentialsId)
    } else {
         execSVN("add $commitPath", svnCredentialsId)
    }
    execSVN("commit -m \"$message - BNO: $BUILD_NUMBER\" $commitPath", svnCredentialsId)
}

// commit a given path to.
def commit(svnCredentialsId, commitPath, message="#INF-1476 - pipeline commit") { 
    execSVN("commit -m \"$message - BNO: $BUILD_NUMBER\" $commitPath", svnCredentialsId)
}

// Remove given path from svn.
def remove_and_commit(svnCredentialsId, svnRoot, relativeRemotePath, message="#INF-1476 - pipeline commit") { 
    execSVN("delete ${svnRoot}/${relativeRemotePath} -m \"$message - BNO: $BUILD_NUMBER\"", svnCredentialsId)
}

// Check the existency of a remote path.
def isSvnPathExists(svnCredentialsId, svnRoot, relativeRemotePath) {
    try{
        execSVN("ls ${svnRoot}/${relativeRemotePath}", svnCredentialsId)
        return true;
    }catch(all){
        return false;
    }
}

// Main svn method credentials are getting from jenkins variables
def execSVN(svnCommandLine, svnCredentialsId) {
    withCredentials([usernamePassword(credentialsId: svnCredentialsId, passwordVariable: 'SVN_PASSWORD', usernameVariable: 'SVN_USERNAME')]) {
        def svnArgs = "--username \"$SVN_USERNAME\" --password \"$SVN_PASSWORD\" --no-auth-cache"
        sh returnStdout: true, script: "svn $svnArgs $svnCommandLine"
    }
}

return this