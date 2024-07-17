#!/bin/bash

# adjust permission of private key
chmod 600 ./BFTBrain/scripts/miyuki/id_cloudlab

# install pip3 and dependencies
sudo apt-get update && sudo apt-get install -y python3-pip
pip3 install -r ./BFTBrain/scripts/requirements.txt

# demo
sudo apt-get install -y python3-venv
cd ./BFTBrain/demo/server
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
