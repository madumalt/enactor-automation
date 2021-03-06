#!/usr/bin/env python3
# preformat user input before write to the file
import fileinput
import sys

ad_username = sys.argv[2]
ad_password = sys.argv[3]

with fileinput.FileInput(sys.argv[1], inplace=True) as file:
    for line in file:
        line = line.replace('YOUR_ACTIVE_DIRECTORY_USERNAME', ad_username)
        print(line.replace('YOUR_ACTIVE_DIRECTORY_PASSWORD', ad_password), end='')
