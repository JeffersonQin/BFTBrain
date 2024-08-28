import json
from fastapi import FastAPI, Form
from typing import Any
import subprocess
import ipaddress
import yaml
import os
import threading
import pandas as pd
import datetime
import atexit
import argparse
import uvicorn
import copy


parser = argparse.ArgumentParser()
parser.add_argument("--host", "-H", type=str, default="0.0.0.0", help="host of the backend, default to be 0.0.0.0")
parser.add_argument("--port", "-p", type=int, default=8999, help="port of the backend, default to be 8999")
args = parser.parse_args()

app = FastAPI()

data_store_lock = threading.Lock()

# state variables
configs = []
data_store = {}
config_updated = []
current_session_name = ""
started = False

# current dir
root_dir = os.path.dirname(os.path.abspath(__file__))

# ===============================================================================
# default.yaml modify backend server field to be public IP, write to another file
result = subprocess.run([
    "bash",
    "-c",
    "~/BFTBrain/scripts/get_ip.sh"
], stdout=subprocess.PIPE)
server_host = result.stdout.decode().strip()
# check whether is valid ipv4
ipaddress.ip_address(server_host)
if type(ipaddress.ip_address(server_host)) is not ipaddress.IPv4Address:
    raise Exception("IP IS NOT IPv4")
print("Current IP:", server_host)

with open(os.path.join(root_dir, "default.yaml"), "r") as f:
    data = yaml.safe_load(f)

data['demo']['update_server'] = server_host
data['demo']['update_port'] = args.port

class Dumper(yaml.Dumper):
    def increase_indent(self, flow=False, *args, **kwargs):
        return super().increase_indent(flow=flow, indentless=False)

def update_config_file(updated_data):
    yaml.dump(updated_data, open(os.path.join(root_dir, "default-updated.yaml"), "w"), default_flow_style=False, Dumper=Dumper)

update_config_file(data)
# ===============================================================================


def save_data():
    # for each key, data frame pair in data_store
    # save data frame to csv
    # the path should be root_dir/data/{key}.csv
    # only save when path not exists

    with data_store_lock:
        os.makedirs(os.path.join(root_dir, "data"), exist_ok=True)

        for key, df in data_store.items():
            path = os.path.join(root_dir, "data", key + ".csv")
            if not os.path.exists(path):
                df.to_csv(path, index=False)


def load_data():
    # for each csv file in root_dir/data
    # read csv file to data frame
    # the key should be the file name without extension
    # only load when path exists

    with data_store_lock:
        os.makedirs(os.path.join(root_dir, "data"), exist_ok=True)

        for file in os.listdir(os.path.join(root_dir, "data")):
            path = os.path.join(root_dir, "data", file)
            if os.path.isfile(path) and path.endswith(".csv"):
                key = file[:-4]
                data_store[key] = pd.read_csv(path)


load_data()
atexit.register(save_data)


@app.get("/start")
async def start_session(name: str, username: str, password: str, experiment_name: str, experiment_profile: str, protocol: str):
    global config_updated
    global current_session_name
    global started

    if not started:
        # custom protocol
        if protocol not in data['switching']['protocol-pool']:
            updated_config = copy.deepcopy(data)
            updated_config['switching']['protocol-pool'].append(protocol)
            update_config_file(updated_config)

        # session_name = YYYY-MM-DD xx:xx:xx + session_name
        name = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S") + " " + name + " - " + protocol
        # record current session name
        current_session_name = name

        if current_session_name not in data_store.keys():
            data_store[current_session_name] = pd.DataFrame(columns=["metrics_name", "id", "timestamp", "value"])
        # clear config updated units
        config_updated = []

        # run commands
        subprocess.run([
            "bash",
            "-c",
            "if tmux has-session -t bftgym 2>/dev/null; then\n" +
            "  tmux kill-session -t bftgym\n" +
            "fi"
        ]).check_returncode()

        subprocess.run([
            "bash",
            "-c",
            "tmux new-session -d -s bftgym"
        ]).check_returncode()

        subprocess.run([
            "bash",
            "-c",
            f"tmux send-keys -t bftgym:0 \"USER='{username}' PWORD='{password}' ~/BFTBrain/scripts/miyuki/main.py -e {experiment_name} -p {experiment_profile} single {protocol} --config ~/BFTBrain/demo/server/default-updated.yaml\" C-m"
        ]).check_returncode()

        started = True

    return {"code": 1, "message": "Session started successfully", "name": current_session_name}


@app.get("/stop")
async def stop_session():
    global started
    global config_updated
    if started:
        for _ in range(3): # fault tolerant
            subprocess.run([
                "bash",
                "-c",
                "if tmux has-session -t bftgym 2>/dev/null; then\n" +
                "  tmux send-keys -t bftgym:0 \"stop\" C-m\n" +
                "fi"
            ]).check_returncode()

        started = False
        config_updated = []

        save_data()

    return {"code": 1, "message": "Session stopped successfully"}


@app.get("/configs")
async def get_configs(unit_id: int):
    global config_updated
    if unit_id not in config_updated:
        config_updated.append(unit_id)
    # return config
    return configs


@app.put("/configs")
async def update_configs(new_configs: str = Form(...)):
    # new_configs format: 
    # { type: str, key: str, value: str } []
    # update config data
    global configs
    global config_updated
    configs = json.loads(new_configs)
    # clear config updated units
    config_updated = []
    return {"code": 1, "message": "Configs updated successfully"}


@app.get("/status")
async def get_status():
    return config_updated


@app.get("/sessions")
async def get_sessions():
    return list(data_store.keys())


@app.put("/data")
async def upload_data(metrics_name: str = Form(...), id: str = Form(...), timestamp: int = Form(...), value: str = Form(...)):
    print("Upload data:", metrics_name, id, timestamp, value)

    with data_store_lock:
        new_data = {
            "metrics_name": metrics_name,
            "id": id,
            "timestamp": timestamp,
            "value": value
        }
        data_store[current_session_name] = pd.concat([data_store[current_session_name], pd.DataFrame([new_data])], ignore_index=True)

    return {"code": 1, "message": "Data uploaded successfully"}


@app.get("/data")
async def fetch_data(metrics_name: str, session_name: str):
    print("Fetch data:", metrics_name, session_name)

    with data_store_lock:
        if session_name not in data_store.keys():
            return []

        return data_store[session_name][data_store[session_name]["metrics_name"] == metrics_name].to_dict(orient="records")


if __name__ == '__main__':
    uvicorn.run(app, host=args.host, port=args.port)
