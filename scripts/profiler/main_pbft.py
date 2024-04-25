import sys
from item import *
from extractor import *
from tqdm import tqdm
import pandas as pd
import matplotlib.pyplot as plt
from datetime import datetime

if len(sys.argv) < 3 or len(sys.argv) > 4:
    print("Usage: python main_pbft.py <log_file> <batch_size> [optional <num-nodes>]")
    sys.exit(1)

log_file = sys.argv[1]
batch_size = int(sys.argv[2])
num_nodes_4 = False

if len(sys.argv) == 4:
    if sys.argv[3] == "4":
        num_nodes_4 = True

log_items = extract(log_file)

timestamps = {}

def update(seqnum, key, value):
    if seqnum not in timestamps.keys():
        timestamps[seqnum] = {}
    
    timestamps[seqnum][key] = value


def is_in(seqnum, key):
    if seqnum not in timestamps.keys():
        return False
    if key not in timestamps[seqnum].keys():
        return False
    return True


def tally(seqnum, key):
    if seqnum not in timestamps.keys():
        timestamps[seqnum] = {}
    
    if key not in timestamps[seqnum].keys():
        timestamps[seqnum][key] = 0
    
    timestamps[seqnum][key] += 1


def append(seqnum, key, value):
    if seqnum not in timestamps.keys():
        timestamps[seqnum] = {}
    
    if key not in timestamps[seqnum].keys():
        timestamps[seqnum][key] = []
    
    timestamps[seqnum][key].append(value)


def delta_ns(start: str, end: str) -> int:
    return (pd.to_datetime(end, format='%Y-%m-%d %H:%M:%S.%f') - pd.to_datetime(start, format='%Y-%m-%d %H:%M:%S.%f')).value


for item in tqdm(log_items, desc=f"Filtering"):
    if isinstance(item, ProcessLogItem):
        if item.message_type == "REQUEST":
            if (item.sequence_number + 1) % batch_size == 1:
                seqnum = item.sequence_number // batch_size
                update(seqnum, "PREPREPARE_START", item.timestamp)
        elif item.message_type == "PREPREPARE":
            update(item.sequence_number, "PREPARE_START", item.timestamp)
        elif num_nodes_4 and item.message_type == "COMMIT":
            append(item.sequence_number, "COMMIT_RECIEVED", item.from_)
    elif isinstance(item, SendLogItem):
        if item.message_type == "PREPREPARE":
            update(item.sequence_number, "PREPREPARE_END", item.timestamp)
            update(item.sequence_number, "PREPARE_START", item.timestamp)
        elif item.message_type == "COMMIT":
            update(item.sequence_number, "PREPARE_END", item.timestamp)
            update(item.sequence_number, "COMMIT_START", item.timestamp)
        elif item.message_type == "REPLY":
            update(item.sequence_number, "EXECUTION_END", item.timestamp)
    elif isinstance(item, ExecutionItem):
        update(item.sequence_number, "COMMIT_END", item.timestamp)
        update(item.sequence_number, "EXECUTION_START", item.timestamp)
    elif isinstance(item, StateUpdateLoopLogItem):
        tally(item.sequence_number, "STATE_UPDATE_LOOP")
        

keys = list(timestamps.keys())
keys.sort()

pp_x = []
pp_intervals = []
p_x = []
p_intervals = []
c_x = []
c_intervals = []
s_x = []
s_cnt = []
cq_x = []
cq_q = []
e_x = []
e_intervals = []

for key in tqdm(keys, desc=f"Calculating deltas"):
    if "PREPREPARE_START" in timestamps[key].keys() and "PREPREPARE_END" in timestamps[key].keys():
        pp_x.append(key)
        pp_intervals.append(delta_ns(timestamps[key]["PREPREPARE_START"], timestamps[key]["PREPREPARE_END"]))
    if "PREPARE_START" in timestamps[key].keys() and "PREPARE_END" in timestamps[key].keys():
        p_x.append(key)
        p_intervals.append(delta_ns(timestamps[key]["PREPARE_START"], timestamps[key]["PREPARE_END"]))
    if "COMMIT_START" in timestamps[key].keys() and "COMMIT_END" in timestamps[key].keys():
        c_x.append(key)
        c_intervals.append(delta_ns(timestamps[key]["COMMIT_START"], timestamps[key]["COMMIT_END"]))
    if "STATE_UPDATE_LOOP" in timestamps[key].keys():
        s_x.append(key)
        s_cnt.append(timestamps[key]["STATE_UPDATE_LOOP"])
    if num_nodes_4 and "COMMIT_RECIEVED" in timestamps[key].keys():
        cq_x.append(key)
        r = timestamps[key]["COMMIT_RECIEVED"]
        ret = list({0, 1, 2, 3} - set(r[:3]))[0]
        cq_q.append(ret)
    if "EXECUTION_START" in timestamps[key].keys() and "EXECUTION_END" in timestamps[key].keys():
        e_x.append(key)
        e_intervals.append(delta_ns(timestamps[key]["EXECUTION_START"], timestamps[key]["EXECUTION_END"]))
    

# Plotting
fig, axs = plt.subplots(3, 2)

axs[0, 0].plot(pp_x, pp_intervals, label='preprepare intervals')
axs[0, 0].set_xlabel('seqnum')
axs[0, 0].set_ylabel('duration (ns)')
axs[0, 0].set_title('Preprepare Intervals')
axs[0, 0].legend()

axs[0, 1].plot(p_x, p_intervals, label='prepare intervals')
axs[0, 1].set_xlabel('seqnum')
axs[0, 1].set_ylabel('duration (ns)')
axs[0, 1].set_title('Prepare Intervals')
axs[0, 1].legend()

axs[1, 0].plot(c_x, c_intervals, label='commit intervals')
axs[1, 0].set_xlabel('seqnum')
axs[1, 0].set_ylabel('duration (ns)')
axs[1, 0].set_title('Commit Intervals')
axs[1, 0].legend()

axs[1, 1].plot(e_x, e_intervals, label='execution intervals')
axs[1, 1].set_xlabel('seqnum')
axs[1, 1].set_ylabel('duration (ns)')
axs[1, 1].set_title('Execution Intervals')
axs[1, 1].legend()

axs[2, 0].plot(s_x, s_cnt, label='state update loop count')
axs[2, 0].set_xlabel('seqnum')
axs[2, 0].set_ylabel('count')
axs[2, 0].set_title('State Update Loop Tally')
axs[2, 0].legend()

axs[2, 1].scatter(cq_x, cq_q, label='node outside commit quorum', s=1)
axs[2, 1].set_xlabel('seqnum')
axs[2, 1].set_ylabel('node id')
axs[2, 1].set_title('Commit Quorum')
axs[2, 1].legend()

# Generate timestamp
timestamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')

# Adjust the spacing between subplots
plt.subplots_adjust(left=0.1, right=0.9, bottom=0.1, top=0.9, wspace=0.3, hspace=0.3)

# Set the figure size
fig.set_size_inches(10, 12)

plt.suptitle(f"PBFT: {log_file} (B={batch_size})")

# Save as PDF with timestamp in filename
filename = f'plots/{timestamp}_pbft_intervals_plot.pdf'
plt.savefig(filename)
