#!/bin/bash
if [[ $(lsof -t -i :9020) ]]; then
    echo "Process $(lsof -t -i :9020) not killed, killing ..."
    kill -9 $(lsof -t -i :9020)
else
    echo "Process killed"
fi
