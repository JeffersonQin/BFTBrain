import subprocess
import os
import sys
import argparse

parser = argparse.ArgumentParser(description='Start a BFTBrain reproduce experiment.')
parser.add_argument('--trial', '-t', type=int, default=10, help='number of trials to reproduce (default: 10)')
parser.add_argument('--duration', '-d', type=int, default=300, help='duration of each trial (sec) (default: 300)')
parser.add_argument('--local', action='store_true', default=False, help='whether to use local network, false by default')

args = parser.parse_args()

trial = args.trial
trial_duration = args.duration
local = args.local

files = os.listdir("reproduce")
files.sort()

for file in files:
    with open(os.path.join("reproduce", file), "r") as f:
        content = f.read()
    with open("config.gridsearch.output.yaml", "w") as f:
        f.write(content)
    for i in range(trial):
        if local:
            subprocess.run(["bash", "./run.sh", f"{trial_duration}", "--"])
        else:
            subprocess.run(["bash", "./run.sh", f"{trial_duration}"])
