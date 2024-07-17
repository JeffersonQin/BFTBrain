import gradio as gr
import yaml
import os
import argparse
import math
import random
import requests
import json
import pandas as pd
import datetime


parser = argparse.ArgumentParser()
parser.add_argument("--fault", "-f", type=int, default=1, help="f in BFT system, default is 1")
parser.add_argument("--backend", "-b", type=str, default="http://127.0.0.1:8999", help="backend server, default is localhost:8999")
args = parser.parse_args()


num_units = 3 * args.fault + 3 # 3f+1 + (client) + (coordination server)
num_entities = 3 * args.fault + 2 # 3f+1 + (client)

units_config_status = [False] * num_units

current_session_name = ""

config_widgets = []
config_metas = []

root_dir = os.path.dirname(os.path.abspath(__file__))
config_path = os.path.join(root_dir, "configs.yaml")

with open(config_path, "r") as f:
    config = yaml.load(f, Loader=yaml.FullLoader)['configurations']

empty_df = pd.DataFrame(columns=["metrics_name", "id", "timestamp", "value"])


def instantiate_configuration_fields():
    for group in config:
        group_name = group['group']
        fields = group['fields']

        with gr.Accordion(label=group_name, open=False):
            with gr.Column():
                for field in fields:
                    desc = field['desc']
                    key = field['key']
                    type = field['type']
                    default_val = field['value']

                    if type == 'boolean':
                        itf = gr.Radio(label=desc, choices=['True', 'False'], value=str(default_val), interactive=True)
                    else:
                        itf = gr.Textbox(label=desc, value=default_val, interactive=True)

                    config_widgets.append(itf)
                    config_metas.append({'key': key, 'type': type})


def get_unit_name(unit_id):
    if unit_id == 0:
        return "Controller"
    if unit_id == num_units - 1:
        return "Client"
    return f"Replica {unit_id - 1}"


def get_units_status():
    # get updated units
    units_config_status = [False] * num_units
    updated_units = requests.get(f"{args.backend}/status").json()
    for unit in updated_units:
        if unit < num_units:
            units_config_status[unit] = True


    ret = "<div align=\"center\" style=\"margin: 0 auto;\">\n"
    ret += "<h3>Units Status</h3>\n"
    ret += "<table>\n"

    num_columns = 4
    num_rows = (num_units + num_columns - 1) // num_columns

    for row in range(num_rows):
        ret += "<tr>\n"
        for col in range(num_columns):
            unit_index = row * num_columns + col
            if unit_index < num_units:
                if units_config_status[unit_index]:
                    ret += f"<td style=\"color:green\">{get_unit_name(unit_index)}: Updated</td>\n"
                else:
                    ret += f"<td style=\"color:gray\">{get_unit_name(unit_index)}: Waiting</td>\n"
            else:
                ret += "<td></td>\n"  # Empty cell for padding

        ret += "</tr>\n"

    ret += "</table>\n"
    ret += "</div>"

    return ret


def get_throughput(session_name: str):
    throughputs = requests.get(f"{args.backend}/data", params={"session_name": session_name, "metrics_name": "throughput"}).json()

    if len(throughputs) == 0:
        throughputs_df = empty_df.copy()
    else:
        throughputs_df = pd.DataFrame(throughputs)

    # print(throughputs_df)

    # Convert 'value' column to string and then replace 'req/s' with an empty string
    throughputs_df['value'] = throughputs_df['value'].apply(lambda x: float(x.replace('req/s', '')))
    throughputs_df = throughputs_df.rename(columns={'value': 'throughput'})

    # Convert 'timestamp' column to datetime object
    throughputs_df['timestamp'] = pd.to_datetime(throughputs_df['timestamp'], unit='s')

    # Drop 'metrics_name' column
    throughputs_df = throughputs_df.drop('metrics_name', axis=1)
    # Drop 'id' column
    throughputs_df = throughputs_df.drop('id', axis=1)

    return throughputs_df


def get_current_throughput():
    return get_throughput(current_session_name)


def get_last_commited():
    last_committed = requests.get(f"{args.backend}/data", params={"session_name": current_session_name, "metrics_name": "last-executed-sequence"}).json()

    if len(last_committed) == 0:
        last_committed_df = empty_df.copy()
    else:
        last_committed_df = pd.DataFrame(last_committed)

    last_committed_df['value'] = last_committed_df['value'].apply(lambda x: int(x.replace('num: ', '')))
    last_committed_df = last_committed_df.rename(columns={'value': 'last_commited', 'id': 'Unit'})

    last_committed_df['timestamp'] = pd.to_datetime(last_committed_df['timestamp'], unit='s')
    last_committed_df['Unit'] = last_committed_df['Unit'].apply(lambda x: get_unit_name(int(x) + 1))

    last_committed_df = last_committed_df.drop('metrics_name', axis=1)

    return last_committed_df


def update_configs(*widgets):
    new_configs = []
    for meta, value in zip(config_metas, widgets):
        new_configs.append({
            'type': meta['type'],
            'key': meta['key'],
            'value': value
        })
    new_configs = json.dumps(new_configs)

    response = requests.put(f"{args.backend}/configs", data={"new_configs": new_configs})

    if response.status_code == 200:
        print("Configs updated successfully")
    else:
        print("Configs updated failed")


def get_sessions():
    response = requests.get(f"{args.backend}/sessions")
    if response.status_code == 200:
        session_db.choices = response.json()
        print("Sessions updated successfully")
    else:
        print("Sessions updated failed")

    session_list = response.json()
    # sort
    session_list.sort()

    return session_list


def start_session(session_name, username, password, experiment_name, experiment_profile, protocol, custom_protocol):
    global current_session_name

    if protocol == 'custom':
        protocol = custom_protocol

    response = requests.get(f"{args.backend}/start", params={
        "name": session_name,
        "username": username,
        "password": password,
        "experiment_name": experiment_name,
        "experiment_profile": experiment_profile,
        "protocol": protocol
    })

    if response.status_code == 200:
        print("Session started successfully")
        current_session_name = response.json()['name']

        return gr.Dropdown(value=current_session_name, choices=get_sessions())
    else:
        print("Session started failed")

        return gr.Dropdown()


def stop_session():
    global units_config_status
    response = requests.get(f"{args.backend}/stop")

    if response.status_code == 200:
        print("Session stopped successfully")
    else:
        print("Session stopped failed")


def fetch_sessions():    
    return gr.Dropdown(choices=get_sessions())


def specify_sessions(session_name):
    global current_session_name
    current_session_name = session_name


def update_compare_throughput(sessions_to_compare):
    if len(sessions_to_compare) == 0:
        return pd.DataFrame(columns=["timestamp", "throughput", "trial"])

    dfs = []

    max_time = 1

    # get data
    for session in sessions_to_compare:
        session_throughput = get_throughput(session)

        # rename id to trial, value set to session name
        session_throughput = session_throughput.rename(columns={'id': 'trial'})
        session_throughput['trial'] = session

        # turn timestamp to offset second to the first timestamp
        session_throughput['timestamp'] = session_throughput['timestamp'].apply(lambda x: (x - session_throughput['timestamp'][0]).total_seconds())

        # rename timestamp to time elapsed (s)
        session_throughput = session_throughput.rename(columns={'timestamp': 'time elapsed (s)'})

        max_time = max(max_time, session_throughput['time elapsed (s)'].max())

        dfs.append(session_throughput)

    # concat
    throughput_compare_df = pd.concat(dfs, ignore_index=True)

    return gr.LinePlot(value=throughput_compare_df, x_lim=[0, max_time])


theme = gr.themes.Soft()

with gr.Blocks(theme=theme, css="""
    textarea {
        font-weight: bold;
    }
    span {
        font-weight: bold;
    }
    div {
        font-weight: bold;
    }
    button {
        font-weight: bold;
    }
    """) as demo:
    with gr.Column():
        gr.Markdown("# BFTGym: An Interactive Playground for BFT Protocols")

        with gr.Tab("Interactive Playground"):
            with gr.Column():
                with gr.Row():
                    # configurations
                    with gr.Column():
                        gr.Markdown("## Step 1 - Configurations")
                        # configuration fields
                        instantiate_configuration_fields()
                        # update button
                        config_update_btn = gr.Button(value="Update")
                        # update status
                        gr.HTML(value=get_units_status, every=1)
                        # update button action
                        config_update_btn.click(update_configs, inputs=config_widgets)
                        # server configurations
                        with gr.Accordion(label="Cloudlab Configurations", open=False):
                            username_tb = gr.Textbox(label="Username", value="root", interactive=True)
                            passwd_tb = gr.Textbox(label="Password", value="root", interactive=True, type="password")
                            experiment_name_tb = gr.Textbox(label="Experiment Name", value="demo-test", interactive=True)
                            experiment_profile_tb = gr.Textbox(label="Experiment Profile", value="m510-f-1", interactive=True)
                    # plots
                    with gr.Column():
                        gr.Markdown("## Results - Plots")
                        # choose sessions
                        with gr.Row():
                            session_db = gr.Dropdown(label="Session", choices=[], interactive=True)
                            session_update_btn = gr.Button(value="🔄")
                            session_update_btn.click(fetch_sessions, outputs=session_db)

                            session_db.input(specify_sessions, inputs=session_db)

                        throughput_plot = gr.LinePlot(value=get_current_throughput, x="timestamp", y="throughput", color_legend_position="bottom", title="System Throughput", tooltip=["timestamp", "throughput"], interactive=True, width=600, every=1)

                        last_commited_plot = gr.LinePlot(value=get_last_commited, x="timestamp", y="last_commited", color="Unit", color_legend_position="bottom", title="Last Commited Sequence Number", tooltip=["timestamp", "last_commited", "Unit"], interactive=True, width=600, every=1)
                # control panel
                with gr.Column():
                    gr.Markdown("## Step 2 - Control Panel")
                    with gr.Row():
                        session_tb = gr.Textbox(label="Trial Name", info="Display name for the trial", value="default", interactive=True)
                        protocol_db = gr.Dropdown(label="Protocol", info="Protocol to run for the trial", choices=["pbft", "sbft", "hotstuff", "zyzzyva", "prime", "cheapbft", "custom"], value="pbft", interactive=True)
                        custom_protocol_tb = gr.Textbox(label="Custom Protocol", info="Only applicable when protocol selected to custom", value="", interactive=True)
                    with gr.Row():
                        start_btn = gr.Button(value="Start", variant="primary")
                        stop_btn = gr.Button(value="Stop", variant="stop")

                        start_btn.click(start_session, inputs=[session_tb, username_tb, passwd_tb, experiment_name_tb, experiment_profile_tb, protocol_db, custom_protocol_tb], outputs=session_db)
                        stop_btn.click(stop_session)

        with gr.Tab("Results Comparison"):
            with gr.Row():
                with gr.Column():
                    sessions_compare_db = gr.Dropdown(choices=[], interactive=True, multiselect=True, value=[], label="Trials to compare", info="Select multiple trials to compare the statistics on the right.")

                    session_compare_update_btn = gr.Button(value="🔄")
                    session_compare_update_btn.click(fetch_sessions, outputs=sessions_compare_db)

                with gr.Column():
                    throughput_compare_plot = gr.LinePlot(value=pd.DataFrame(columns=["time elapsed (s)", "throughput", "trial"]), x="time elapsed (s)", y="throughput", color="trial", color_legend_position="right", title="System Throughput", tooltip=["time elapsed (s)", "throughput", "trial"], interactive=True, width=600)

            sessions_compare_db.input(update_compare_throughput, inputs=sessions_compare_db, outputs=throughput_compare_plot)


demo.launch(server_name="0.0.0.0", server_port=7860, share=True)
