protocol=$1
count=$2
host_ip=$3
duration=$4

echo "\033[4;34mStart running $protocol with $count servers\033[m"

echo "Protocol $protocol : [0/6] Killing previous processes if needed ..."

for (( i=0; i<$count; i++ ))
do
  echo "Killing Bedrock on machine $i ..."
  # bedrock
  tmux send-keys -t cloudlab:"$i" "../scripts/kill_process_port.sh 9020" C-m
  # learning agent
  tmux send-keys -t cloudlab:"$i" "../scripts/kill_process_port.sh 9040" C-m
done

echo "Protocol $protocol : [1/6] Starting Coordination Server"
# start server on the first server
tmux send-keys -t cloudlab:0 "./run.sh CoordinatorServer -p 9020 -r $protocol" C-m

echo "Protocol $protocol : [2/6] Waiting for 10 seconds for coordination server to set up ..."
sleep 10

# start units
for (( i=1; i<$count-1; i++ ))
do
  echo "Protocol $protocol : [3/6] Starting Coordination Unit $(($i-1))"
  tmux send-keys -t cloudlab:"$i" "./run.sh CoordinatorUnit -u $(($i-1)) -p 9020 -n 1 -s $host_ip:9020" C-m
  sleep 1
done

# start learning agent
# only have agent for *unit*
# no agent for coordination server and client
# use the same notation as last section
for (( i=1; i<$count-1; i++ ))
do
  echo "Protocol $protocol : [3/6] Starting Learning Agent for Unit $(($i-1))"
  tmux send-keys -t cloudlab-learning:"$i" "cd ~/BFTBrain/code/learning/ && python3 learning_agent.py -u $(($i-1)) -p 9020" C-m
done

sleep 5

echo "Protocol $protocol : [4/6] Starting Client"
# start client
tmux send-keys -t cloudlab:"$(($count-1))" "./run.sh CoordinatorUnit -u $(($count-2)) -p 9020 -c 1 -s $host_ip:9020" C-m

echo "Protocol $protocol : [5/6] Waiting for 10 seconds for connection to set up ..."
sleep 10

# coordination server start
tmux send-keys -t cloudlab:0 C-m

echo "Protocol $protocol : [6/6] Executing ..."

# if duration is 0, that means to run indefinitely
# and stop until receiving EOF
if [ $duration -eq 0 ]; then
  cat
else # otherwise just run normally
  sleep $duration
fi

count=0
while IFS= read -r line || [[ -n "$line" ]]; do
  echo "Stopping Bedrock on $line"
  tmux send-keys -t cloudlab:"$count" C-c
  tmux send-keys -t cloudlab-learning:"$count" C-c
  ((count++))
done < servers.txt

echo "Protocol $protocol : [Finish]"
sleep 10
