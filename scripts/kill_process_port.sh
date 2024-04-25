#!/bin/bash
if [[ $(lsof -t -i :"$1") ]]; then
    echo "Process $(lsof -t -i :"$1") not killed, killing ..."
    kill -9 $(lsof -t -i :"$1")
else
    echo "Process killed"
fi
