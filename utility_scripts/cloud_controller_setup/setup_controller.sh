#!/bin/bash
# Cloud controller setup script for the Cloudcontroller ISO

DOWNLOAD_LOCATION="/var/SelfServiceApp";
DOWNLOAD_LOCATION_CONTENTS="$DOWNLOAD_LOCATION/.*";
SVN_USER="$1";
SVN_PWD="$2";
SELF_SERVICE_SVN_LOCATION_TRUNK="$3";

rm -rf $DOWNLOAD_LOCATION_CONTENTS
svn co "$SELF_SERVICE_SVN_LOCATION_TRUNK" "$DOWNLOAD_LOCATION" --username "$SVN_USER" --password "$SVN_PWD"

python3 add-credentials-to-jenkins-env-file.py "$DOWNLOAD_LOCATION/cloud_controller/docker/jenkins.env" "$SVN_USER" "$SVN_PWD"

groupadd docker

usermod -aG docker "install"

docker volume prune -f

echo "cloud controller successfully configured. Please restart the machine to continue."
