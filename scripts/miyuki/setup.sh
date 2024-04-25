#!/bin/bash

# adjust permission of private key
chmod 600 ./BFTBrain/scripts/miyuki/id_cloudlab

# install pip3 and dependencies
sudo apt-get update && sudo apt-get install -y python3-pip
pip3 install -r ./BFTBrain/scripts/requirements.txt
