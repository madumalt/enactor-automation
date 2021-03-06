#!/bin/bash
echo "${username}:${password}" | sudo chpasswd
echo "root:${password}" | sudo chpasswd
sudo sed -ri 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/g' /etc/ssh/sshd_config
sudo sed -ri 's/PasswordAuthentication no/PasswordAuthentication yes/g' /etc/ssh/sshd_config
sudo sed -ri 's/PubkeyAuthentication yes/PubkeyAuthentication no/g' /etc/ssh/sshd_config
sudo service sshd restart
sudo service ssh restart