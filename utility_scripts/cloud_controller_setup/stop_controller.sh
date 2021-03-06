#!/bin/bash
# Cloud controller stop service script for the Cloudcontroller ISO

FILE_LOCATION="/var/SelfServiceApp/cloud_controller";

cd $FILE_LOCATION && docker-compose down -v && echo "cloud controller successfully stopped."