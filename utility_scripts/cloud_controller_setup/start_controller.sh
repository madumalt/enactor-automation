#!/bin/bash
# Cloud controller start service script for the Cloudcontroller ISO

FILE_LOCATION="/var/SelfServiceApp/cloud_controller";

cd $FILE_LOCATION && docker-compose up -d --build 


export JENKINS_IP=`ip -4 addr show ens33 | grep -Po 'inet \K[\d.]+'`
echo "Started cloud controller. http://$JENKINS_IP:8888"
