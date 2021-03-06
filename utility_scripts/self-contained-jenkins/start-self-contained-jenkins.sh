#!/bin/bash

# Initiate and seflcontained Jenkins with Enactor Deployment Tool.

# Unzipped folder location (absolute path) should be given as the first parameter to this script.
# In the given "cloud_controller location it must contain the jenkins.env with correct configs."

SELF_CONTAINED_JENKINS_HOME="$1";
CLOUD_CONTROLLER_NAME="cloud_controller"
CLOUD_CONTROLLER_HOME="$SELF_CONTAINED_JENKINS_HOME/$CLOUD_CONTROLLER_NAME"

print_help() {
    echo "Help:"
    echo "Run the start-self-contained-jenkins.sh as follows;"
    echo "$ ./start-self-contained-jenkins.sh <absolute path of unzipped location>"
    echo ""
}

cd "$CLOUD_CONTROLLER_HOME" \
&& docker-compose down && docker-compose up --build -d

if [ $? -ne 0 ]; then
    print_help
fi