#!/bin/bash

# Initiate and seflcontained Jenkins with Enactor Deployment Tool.

# Unzipped folder location (absolute path) should be given as the first parameter to this script.
# In the given "cloud_controller location it must contain the jenkins.env with correct configs."

SELF_CONTAINED_JENKINS_HOME="$1";
CLOUD_CONTROLLER_NAME="cloud_controller"
CLOUD_CONTROLLER_HOME="$SELF_CONTAINED_JENKINS_HOME/$CLOUD_CONTROLLER_NAME"

LOCAL_SVN_FOLDER="temp"
SVN_SOURCE_CODE_RELATIVE_PATH="latest"

print_help() {
    echo "Help:"
    echo "Run the init-self-contained-jenkins.sh as follows;"
    echo "$ ./init-self-contained-jenkins.sh <absolute path of unzipped location>"
    echo ""
}

destroy_and_start_jenkins() {
    cd "$CLOUD_CONTROLLER_HOME" \
    && docker-compose down \
    && docker-compose up --build -d \
    && echo "self-contained Jenkins is running at http://<server-ip>:8888"
}

setup_source_code() {
    cd "$SELF_CONTAINED_JENKINS_HOME" \
    && rm -rf "$SVN_SOURCE_CODE_RELATIVE_PATH" && mkdir "$SVN_SOURCE_CODE_RELATIVE_PATH" \
    && rsync -av --exclude="$SVN_SOURCE_CODE_RELATIVE_PATH" --exclude="$CLOUD_CONTROLLER_NAME/jenkins.env" . "$SVN_SOURCE_CODE_RELATIVE_PATH" \
    && svn import --message "Initialze the svn source for self-contained jenkins" "$SVN_SOURCE_CODE_RELATIVE_PATH" "$ACCESSIBLE_SOURCE_SVN_LOCATION/$SVN_SOURCE_CODE_RELATIVE_PATH" \
    --username "$SVN_USER" --password "$SVN_PASSWORD" --force --no-auth-cache \
    && SELF_SERVICE_SVN_LOCATION_TRUNK="$SOURCE_SVN_LOCATION/$SVN_SOURCE_CODE_RELATIVE_PATH" \
    && sed -i 's,SELF_SERVICE_SVN_LOCATION_TRUNK.*,SELF_SERVICE_SVN_LOCATION_TRUNK='"$SELF_SERVICE_SVN_LOCATION_TRUNK"',' "$CLOUD_CONTROLLER_NAME/jenkins.env"
}

if [ ! -z "$SELF_CONTAINED_JENKINS_HOME" ]
then
    if [ -d "$CLOUD_CONTROLLER_HOME" ]
    then
        # Get the required params from jenkins.env
        cd "$CLOUD_CONTROLLER_HOME"
        SVN_USER=$(grep SVN_USR jenkins.env | cut -d '=' -f2)
        SVN_PASSWORD=$(grep SVN_PWD jenkins.env | cut -d '=' -f2)
        SOURCE_SVN_LOCATION=$(grep SELF_SERVICE_SVN_ROOT jenkins.env | cut -d '=' -f2)
        USING_INBUILT_SVN_SERVER=$(grep USING_INBUILT_SVN_SERVER jenkins.env | cut -d '=' -f2)

        if [ "$USING_INBUILT_SVN_SERVER" = true ]
        then
            echo "Using inbuilt svn"

            # Replace svn_server with localhost:7443 such that host can access the svn service.
            ACCESSIBLE_SOURCE_SVN_LOCATION=${SOURCE_SVN_LOCATION/svn_server/localhost:7443}

            # To start the svn server
            destroy_and_start_jenkins
        else
            echo "Using external svn"
            ACCESSIBLE_SOURCE_SVN_LOCATION=$SOURCE_SVN_LOCATION
        fi
        echo "Host accessible svn url $ACCESSIBLE_SOURCE_SVN_LOCATION"

        # Commit the source code to the given svn location under latest.
        # If there is any previously uploaded source-code move them to a {date-time} folder.
        svn ls "$ACCESSIBLE_SOURCE_SVN_LOCATION/$SVN_SOURCE_CODE_RELATIVE_PATH" --username "$SVN_USER" --password "$SVN_PASSWORD" --no-auth-cache
        if [ $? -eq 0 ]
        then
            ARCHIVE_FOLDER_NAME=$(date +%Y-%m-%d_%H-%M-%S)

            cd "$SELF_CONTAINED_JENKINS_HOME" \
            && svn move --message "Archive the current latest folder." "$ACCESSIBLE_SOURCE_SVN_LOCATION/$SVN_SOURCE_CODE_RELATIVE_PATH" "$ACCESSIBLE_SOURCE_SVN_LOCATION/$ARCHIVE_FOLDER_NAME" --username "$SVN_USER" --password "$SVN_PASSWORD" --force --no-auth-cache \
            && setup_source_code \
            && destroy_and_start_jenkins \

            if [ $? -ne 0 ]; then
                print_help
            fi
        else
            setup_source_code \
            && destroy_and_start_jenkins \

            if [ $? -ne 0 ]; then
                print_help
            fi
        fi

    else
        echo "\"$CLOUD_CONTROLLER_HOME\"  does not exists!"
        echo "Please check whether the given unzipped location \"$SELF_CONTAINED_JENKINS_HOME\" is correct."
        print_help
    fi
else
    echo "Unzipped location is not given! Try again with correct unzipped folder location."
    print_help
fi
