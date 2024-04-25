# Usage: ./local_exp.sh [protocol] [learning (optional)]
# Examples:
#        ./local_exp.sh pbft # run pbft without learning agents
#        ./local_exp.sh pbft # run bedrock with learning agents, using pbft as the default protocol
# [protocol]: protocol name to run
# [learning]: if *learning* provided, each bedrock entity will be paired with a local learning agent

protocol=$1


count=6 # 3f+3
agent_count=0
# if learning, agent_count=count-2
if [ $# -gt 1 ]; then
  if [ "$2" == "learning" ]; then
    agent_count=$((count-2))
  fi
fi

# check if session exists
if tmux has-session -t cloudlab 2>/dev/null; then
  # session exists - kill it
  tmux kill-session -t cloudlab
fi
if tmux has-session -t cloudlab-learning 2>/dev/null; then
  # session exists - kill it
  tmux kill-session -t cloudlab-learning
fi

# create new session
tmux new-session -d -s cloudlab
if [ $agent_count -gt 0 ]; then
  tmux new-session -d -s cloudlab-learning
fi

# create one window for each server
for (( i=0; i<$count-1; i++ ))
do
  tmux new-window -t cloudlab
done
for (( i=0; i<$agent_count-1; i++ ))
do
  tmux new-window -t cloudlab-learning
done


sleep 5

echo "\033[4;34mStart running $protocol with $count servers\033[m"

echo "Protocol $protocol : [0/6] Killing previous processes if needed ..."

for (( i=0; i<$count; i++ ))
do
  echo "Killing Bedrock and learning agent on machine $i ..."
  tmux send-keys -t cloudlab:"$i" "cd ~/BFTBrain/code && ../scripts/kill_process_port.sh $((9020+$count)) && ../scripts/kill_process_port.sh $((9020+$count+20))" C-m
done

echo "Protocol $protocol : [1/6] Starting Coordination Server"
# start server on the first server
tmux send-keys -t cloudlab:0 "./run.sh CoordinatorServer -p 9020 -r $protocol" C-m

echo "Protocol $protocol : [2/6] Waiting for 10 seconds for coordination server to set up ..."
sleep 5

# start units
for (( i=1; i<$count-1; i++ ))
do
  echo "Protocol $protocol : [3/6] Starting Coordination Unit $(($i-1))"
  tmux send-keys -t cloudlab:"$i" "./run.sh CoordinatorUnit -u $(($i-1)) -p $((9020+$i)) -n 1 -s 127.0.0.1:9020" C-m
  sleep 1
done

# start learning agents
for (( i=0; i<$agent_count; i++ ))
do
  echo "Protocol $protocol : [3/6] Starting Learning Agent for Coordination Unit $i"
  tmux send-keys -t cloudlab-learning:"$i" "cd ~/BFTBrain/code/learning/ && python3 learning_agent.py -u $i -p $((9021+$i)) -n single" C-m
done
sleep 5

echo "Protocol $protocol : [4/6] Starting Client"
# start client
tmux send-keys -t cloudlab:"$(($count-1))" "./run.sh CoordinatorUnit -u $(($count-2)) -p $((9020+$count-1)) -c 1 -s 127.0.0.1:9020" C-m

echo "Protocol $protocol : [5/6] Waiting for 10 seconds for connection to set up ..."
sleep 5

# coordination server start
tmux send-keys -t cloudlab:0 C-m

echo "Protocol $protocol : [6/6] Executing ..."

echo "WAITING FOR INPUT TO KILL THE INSTANCE"
cat

for (( i=0; i<$count; i++ ))
do
  echo "Stopping Bedrock on machine $i ..."
  tmux send-keys -t cloudlab:"$i" C-c
done
for (( i=0; i<$agent_count; i++ ))
do
  tmux send-keys -t cloudlab-learning:"$i" C-c
done

echo "Protocol $protocol : [Finish]"
