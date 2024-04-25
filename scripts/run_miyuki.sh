#!/bin/bash
# Note: THIS IS JUST A SIMPLE MODIFICATION OF run.sh
#       EXCEPT THAT IT ONLY RUNS *ONE* SPECIFIED PROTOCOL
#       FOR *UNLIMITED* DURATION, IT WILL STOP ON RECEIVING *EOF*
# Usage: ./run_miyuki.sh [protocol] [local (optional)]
# Examples:
#        ./run_miyuki.sh pbft -- # run pbft on local network
#        ./run_miyuki.sh pbft # run pbft on public network
# [protocol]: protocol name to run
# [local]: if *anything* provided, run on local network, otherwise run on public network

######## MODIFICATION HERE START ########
protocol=$1

if [ $# -gt 1 ]; then
  public="false"
else
  public="true"
fi
######### MODIFICATION HERE END #########

# check if session exists
if tmux has-session -t cloudlab 2>/dev/null; then
  # session exists - kill it
  tmux kill-session -t cloudlab
fi
if tmux has-session -t cloudlab-learning 2>/dev/null; then
  # session exists - kill it
  tmux kill-session -t cloudlab-learning
fi

echo "\033[4;42mPrepare [1/6] Creating new tmux session\033[m"
# create new session
tmux new-session -d -s cloudlab
tmux new-session -d -s cloudlab-learning

# create one window for each server
tail -n +2 servers.txt | while IFS= read -r line || [[ -n "$line" ]]; do
  tmux new-window -t cloudlab
  tmux new-window -t cloudlab-learning
done

echo "\033[4;42mPrepare [2/6] Regenerate config based on servers.txt\033[m"
# regenerate the config based on the servers.txt
if [ $public == "true" ]; then
  python3 update_config.py
else
  python3 update_config.py --local
fi

echo "\033[4;42mPrepare [3/6] Sending config to coordination server\033[m"
# send configuration to the first server
sftp -oPort=22 -oStrictHostKeyChecking=no $(head -n1 servers.txt | tr -d '\n'):BFTBrain/config <<< "put config.framework.yaml"

echo "\033[4;42mPrepare [4/6] Obtaining Coordination Server's IP Address\033[m"
# get coordination server's ip address
if [ $public == "true" ]; then
  host_ip=$(ssh $(head -n1 servers.txt | tr -d '\n') -p 22 -o "StrictHostKeyChecking no" "BFTBrain/scripts/get_ip.sh | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'")
else
  host_ip=$(ssh $(head -n1 servers.txt | tr -d '\n') -p 22 -o "StrictHostKeyChecking no" "BFTBrain/scripts/get_ip.sh -- | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'")
fi


echo "\033[4;42mPrepare [5/6] Cleaning previous benchmarks on the coordination server\033[m"
ssh $(head -n1 servers.txt | tr -d '\n') -p 22 -o "StrictHostKeyChecking no" "rm -rf BFTBrain/code/benchmarks"

echo "\033[4;42mPrepare [6/6] Start SSH Connection\033[m"
# start all servers' ssh connection
count=0
while IFS= read -r line || [[ -n "$line" ]]; do
  echo "Starting connection of server $count : $line"
  tmux send-keys -t cloudlab:"$count" "ssh $line -p 22 -o \"StrictHostKeyChecking no\"" C-m
  tmux send-keys -t cloudlab:"$count" "source BFTBrain/scripts/java_config.sh && mkdir -p BFTBrain/code/logs && rm -rf BFTBrain/code/logs && mkdir -p BFTBrain/code/logs && cd BFTBrain/code" C-m
  tmux send-keys -t cloudlab-learning:"$count" "ssh $line -p 22 -o \"StrictHostKeyChecking no\"" C-m
  ((count++))
done < servers.txt

echo "\033[4;42mPrepare [Finishing] Wait 10 seconds for SSH to establish connection ...\033[m"
sleep 10

######## MODIFICATION HERE START ########
./run_single.sh $protocol $count $host_ip 0
######### MODIFICATION HERE END #########

echo "\033[4;42mPacking Benchmark Logs\033[m"

# collect detailed logs from each machine to the first machine
mkdir -p ./logs
rm -r ./logs
mkdir -p ./logs

sleep 5 # try to fix the log missing problem

count=0
while IFS= read -r line || [[ -n "$line" ]]; do
  echo "Collecting detailed log of server $count : $line"
  if [ $count -eq 0 ]; then
    echo "skip coordination server"
  else
    sftp -oPort=22 -oStrictHostKeyChecking=no $line <<< "get -r BFTBrain/code/logs/*.log ./logs"
  fi
  ((count++))
done < servers.txt
sftp -oPort=22 -oStrictHostKeyChecking=no $(head -n1 servers.txt | tr -d '\n') <<< "put -r ./logs BFTBrain/code/logs"
rm -r ./logs

file_name=$(date "+%Y-%m-%d--%H-%M-%S").tar.gz
ssh $(head -n1 servers.txt | tr -d '\n') -p 22 -o "StrictHostKeyChecking no" "cd BFTBrain && tar -czvf $file_name config/config.framework.yaml code/benchmarks code/logs"

echo "\033[4;42mDownload Benchmark Logs\033[m"
# send configuration to the first server
sftp -oPort=22 -oStrictHostKeyChecking=no $(head -n1 servers.txt | tr -d '\n') <<< "get BFTBrain/$file_name"

echo "\033[4;42m========= Finished =========\033[m"
