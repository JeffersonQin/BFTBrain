import re
from typing import List
from item import *
from tqdm import tqdm

import re
from typing import List, Union

def parse_log_item(log_line: str) -> Union[ProcessLogItem, TransitionLogItem, SendLogItem, None]:
    # Regular expressions for the different log item types
    process_regex = r"Processing (extra )?(self via transition )?(?P<message_type>\w+) (?P<sequence_number>\d+:\d+) from (?P<from>\d+) to \[(?P<to>[\d, ]+)\]"
    transition_regex = r"Transition (?P<sequence_number>\d+) from (?P<from_state>\w+) to (?P<to_state>\w+)"
    send_regex = r"Sending message: (?P<message_type>\w+) (?P<sequence_number>\d+:\d+) from \d+ to \[(?P<to>[\d, ]+)\]"
    tally_regex = r"Tally message (extra )?(self via transition )? (?P<message_type>\w+) (?P<sequence_number>\d+:\d+) from (?P<from>\d+) to \[(?P<to>[\d, ]+)\]"
    state_update_loop_regex = r"StateUpdateLoop seqnum: (?P<sequence_number>\d+)"
    execution_regex = r"Execution START: (?P<sequence_number>\d+)"
    
    # Split the log line into timestamp, unit_id, and the rest
    timestamp, unit_id, rest = re.match(r"(?P<timestamp>\d+-\d+-\d+ \d+:\d+:\d+\.\d+) \{(?P<unit_id>\d+)\} (?P<rest>.*)", log_line).groups()
    
    # Try to match each log item type
    match = re.match(process_regex, rest)
    if match:
        seqnum = int(match.group("sequence_number").split(":")[0])
        return ProcessLogItem(timestamp, int(unit_id), seqnum, match.group("message_type"), int(match.group("from")), list(map(int, match.group("to").split(", "))))
    
    match = re.match(transition_regex, rest)
    if match:
        seqnum = int(match.group("sequence_number"))
        return TransitionLogItem(timestamp, int(unit_id), seqnum, match.group("from_state"), match.group("to_state"))
    
    match = re.match(send_regex, rest)
    if match:
        seqnum = int(match.group("sequence_number").split(":")[0])
        return SendLogItem(timestamp, int(unit_id), seqnum, match.group("message_type"), list(map(int, match.group("to").split(", "))))
    
    match = re.match(tally_regex, rest)
    if match:
        seqnum = int(match.group("sequence_number").split(":")[0])
        return TallyLogItem(timestamp, int(unit_id), seqnum, match.group("message_type"), int(match.group("from")), list(map(int, match.group("to").split(", "))))
    
    match = re.match(state_update_loop_regex, rest)
    if match:
        seqnum = int(match.group("sequence_number"))
        return StateUpdateLoopLogItem(timestamp, int(unit_id), seqnum)
    
    match = re.match(execution_regex, rest)
    if match:
        seqnum = int(match.group("sequence_number"))
        return ExecutionItem(timestamp, int(unit_id), seqnum)
    
    return None


def extract(file_name: str) -> List[Union[ProcessLogItem, TransitionLogItem, SendLogItem, None]]:
    # Read the log file
    with open(file_name, "r") as f:
        log_lines = f.readlines()
    
    return list(map(parse_log_item, tqdm(log_lines, desc=f"Parsing log file: {file_name}")))
