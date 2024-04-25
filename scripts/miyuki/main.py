#!/usr/bin/python3

import logging
import sys
import os
import argparse
import subprocess
import time
import multiprocessing as multi

from functools import wraps


logging.basicConfig(
    level=logging.DEBUG,
    format="[%(asctime)s] %(name)s:%(levelname)s: %(message)s"
)

parser = argparse.ArgumentParser(description='Start a BFTBrain experiment.')
parser.add_argument('--experiment', '-e', type=str, required=True, help='Cloudlab experiment name')
parser.add_argument('--profile', '-p', type=str, required=True, help='Cloudlab profile to use')
parser.add_argument('--profile2', '-p2', type=str, required=False, default='', help='Second Cloudlab profile to use (optional)')
parser.add_argument('--project', '-j', type=str, default='bft-evaluation', help='Cloudlab project to use')

subparsers = parser.add_subparsers(dest='action', required=True, help='Action to perform.')

deploy_parser = subparsers.add_parser('deploy', help='Start a new experiment')
gs_parser = subparsers.add_parser('gridsearch', help='Run a grid search (do not include `deploy` as a first step)')
rp_parser = subparsers.add_parser('reproduce', help='Reproduce a set of configurations (do not include `deploy` as a first step)')
single_parser = subparsers.add_parser('single', help='Run a single configuration (do not include `deploy` as a first step)')
collect_parser = subparsers.add_parser('collect', help='Collect results from a cloudlab experiment (do not include `deploy` as a first step)')
terminate_parser = subparsers.add_parser('terminate', help='Terminate a cloudlab experiment')
sync_parser = subparsers.add_parser('sync', help='Sync the code to all nodes in the experiment (including master and workers)')

deploy_parser.add_argument('--no-instantiate', '-n', action='store_true', default=False, required=False, help='Skip instantiating the experiment (default False, i.e. instantiate the experiment). This option can be used when the experiment is manually instantiated on Cloudlab but not yet have the environment setup.')

gs_parser.add_argument('--public', '-u', action='store_true', default=False, required=False, help='Use public IPs for the experiment (default False, i.e. use local IPs)')
gs_parser.add_argument('base_config', type=str, help='Base configuration file to use')
gs_parser.add_argument('grid_config', type=str, help='Gridsearch configuration file to use')

rp_parser.add_argument('--public', '-u', action='store_true', default=False, required=False, help='Use public IPs for the experiment (default False, i.e. use local IPs)')
rp_parser.add_argument('--trial', '-t', default=10, help='number of trials to perform for each config (default 10)')
rp_parser.add_argument('--duration', '-d', default=300, help='duration of each trial (sec) (default: 300)')
rp_parser.add_argument('configs_path', type=str, help='Path to the directory containing the configurations to reproduce')

single_parser.add_argument('--public', '-u', action='store_true', default=False, required=False, help='Use public IPs for the experiment (default False, i.e. use local IPs)')
single_parser.add_argument('--config', '-c', default='', help='Path to the configuration to run, default is to use `code/config.framework.yaml` on this machine')
single_parser.add_argument('protocol', type=str, help='Protocol profile name to run (e.g. pbft)')

args = parser.parse_args()

private_key_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "id_cloudlab")
os.environ['CERT'] = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cloudlab.pem")

if len(args.experiment) > 15:
    logging.error('Experiment name {} is too long (cannot exceed 15 characters)'.format(args.experiment))
    sys.exit(1)

if args.action == 'deploy':
    total_cnt = 4
elif args.action == 'gridsearch':
    total_cnt = 4
elif args.action == 'reproduce':
    total_cnt = 4
elif args.action == 'collect':
    total_cnt = 3
elif args.action == 'terminate':
    total_cnt = 2
elif args.action == 'single':
    total_cnt = 4
elif args.action == 'sync':
    total_cnt = 5

current_cnt = 1

def miyuki_log(log_str: str):
    def log_decorator(func):
        @wraps(func)
        def inner(*args, **kwargs):
            global current_cnt
            print('\x1b[6;37;44m' + f'[{current_cnt}/{total_cnt}] {log_str}' + '\x1b[0m')
            ret = func(*args, **kwargs)
            print('\x1b[6;30;42m' + f'[{current_cnt}/{total_cnt}] Success!' + '\x1b[0m')
            current_cnt += 1
            return ret
        return inner
    return log_decorator


import powder.experiment as pexp


def get_nodes(experiment_name, profile_name) -> pexp.PowderExperiment:
    exp = pexp.PowderExperiment(
        experiment_name=experiment_name,
        project_name=args.project,
        profile_name=profile_name
    )
    
    if args.action == 'deploy' and not args.no_instantiate:
        exp_status = exp.start_and_wait()
    else:
        exp._get_status()
        exp_status = exp.status

    if exp_status != exp.EXPERIMENT_READY:
        logging.error('Failed to start experiment.')
        sys.exit(1)
    
    return exp


def terminate_nodes(experiment_name, profile_name):
    exp = pexp.PowderExperiment(
        experiment_name=experiment_name,
        project_name=args.project,
        profile_name=profile_name
    )
    exp.terminate()


@miyuki_log('Instantiating Master Node ...')
def get_master():
    return get_nodes(f'{args.experiment}m', 'bftbrain-master')


@miyuki_log('Instantiating Worker Nodes ...')
def get_workers():
    workers1 = get_nodes(f'{args.experiment}w', args.profile)
    if args.profile2 != '':
        workers2 = get_nodes(f'{args.experiment}w2', args.profile2)
        for k, v in workers2.nodes.items():
            # workers1 and 2 can have same key, here to distinguish
            workers1.nodes["site2-" + k] = v
    return workers1


@miyuki_log('Terminating Master Node ...')
def terminate_master():
    terminate_nodes(f'{args.experiment}m', 'bftbrain-master')


@miyuki_log('Terminating Worker Nodes ...')
def terminate_workers():
    terminate_nodes(f'{args.experiment}w', args.profile)
    if args.profile2 != '':
        workers2 = terminate_nodes(f'{args.experiment}w2', args.profile2)


def deploy_single_worker(worker_node):
    try:
        subprocess.run(
            [
                "ssh",
                f"{os.environ['USER']}@{worker_node.hostname}",
                "-i",
                private_key_path,
                "-p",
                "22",
                "-o",
                "StrictHostKeyChecking no",
                "wget -O - https://gist.githubusercontent.com/JeffersonQin/04ddbb70868010b781e50527cc92c168/raw/ee1fa45998ebcf815714b63180a50e793adf7f05/BFTBrain-deploy.sh > setup.sh && " + 
                "chmod +x setup.sh && source setup.sh &> setup.log"
            ], 
            stdout = subprocess.DEVNULL,
            stderr = subprocess.DEVNULL
        ).check_returncode()
        print('\x1b[6;30;42m' + f"Successfully deployed on {worker_node.hostname}" + '\x1b[0m')
    except Exception as e:
        print(f"Error occurred on {worker_node.hostname}, check ~/setup.log on that host for more information")
        raise e


@miyuki_log('Deploying Worker Nodes ...')
def deploy_workers(workers):
    ks = list(workers.nodes.keys())
    ks.sort()

    worker_nodes = [workers.nodes[k] for k in ks]
    
    with multi.Pool(processes=len(worker_nodes)) as pool:
        pool.map(deploy_single_worker, worker_nodes)
    
    servers_list_txt = ""
    for worker_node in worker_nodes:
        servers_list_txt = f"{servers_list_txt}{os.environ['USER']}@{worker_node.hostname}\n"

    return servers_list_txt.strip()


@miyuki_log('Deploying Master Node ...')
def deploy_master(master, servers_list_str):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname

    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        "git clone https://github.com/JeffersonQin/BFTBrain && " + 
        f"echo \"{servers_list_str}\" > BFTBrain/scripts/servers.txt && " +
        f"echo \"IdentityFile /users/{os.environ['USER']}/BFTBrain/scripts/miyuki/id_cloudlab\" >> /users/{os.environ['USER']}/.ssh/config && " +
        f"chmod 600 /users/{os.environ['USER']}/BFTBrain/scripts/miyuki/id_cloudlab && " +
        "sudo apt-get update && sudo apt-get install -y python3-pip && " +
        f"pip3 install -r /users/{os.environ['USER']}/BFTBrain/scripts/requirements.txt"
    ]).check_returncode()


@miyuki_log('Uploading Gridsearch Config to Master Node ...')
def upload_gridsearch_config(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname

    subprocess.run([
        "bash",
        "-c",
        f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{master_node_hostname} <<< 'put \"{args.base_config}\" BFTBrain/scripts/config.to.adjust.yaml'"
    ]).check_returncode()
    subprocess.run([
        "bash",
        "-c",
        f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{master_node_hostname} <<< 'put \"{args.grid_config}\" BFTBrain/scripts/config.gridsearch.yaml'"
    ]).check_returncode()


@miyuki_log('Starting Gridsearch on Master Node ...')
def start_gridsearch(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname
    
    tmux_window_name = f"gridsearch-{int(time.time())}"

    if args.public:
        command = "cd BFTBrain/scripts && python3 grid_search.py"
    else:
        command = "cd BFTBrain/scripts && python3 grid_search.py --local"

    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        f"tmux new-session -d -s {tmux_window_name} && " +
        f"tmux send-keys -t {tmux_window_name}:0 \"{command}\" C-m"
    ]).check_returncode()


@miyuki_log('Uploading Reproduction Configs to Master Node ...')
def upload_reproduction_configs(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname

    # delete previous reproduce folder
    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        f"mkdir -p BFTBrain/scripts/reproduce && rm -r BFTBrain/scripts/reproduce"
    ]).check_returncode()

    subprocess.run([
        "bash",
        "-c",
        f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{master_node_hostname} <<< 'put -r \"{args.configs_path}\" BFTBrain/scripts/reproduce'"
    ]).check_returncode()


@miyuki_log('Starting Reproduction on Master Node ...')
def start_reproduction(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname
    
    tmux_window_name = f"reproduce-{int(time.time())}"

    if args.public:
        command = f"cd BFTBrain/scripts && python3 reproduce.py --trial {args.trial} --duration {args.duration}"
    else:
        command = f"cd BFTBrain/scripts && python3 reproduce.py --local --trial {args.trial} --duration {args.duration}"

    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        f"tmux new-session -d -s {tmux_window_name} && " +
        f"tmux send-keys -t {tmux_window_name}:0 \"{command}\" C-m"
    ]).check_returncode()


@miyuki_log('Uploading Single Config to Master Node ...')
def upload_single_config(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname

    # delete previous config
    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        f"echo 'abc' > BFTBrain/scripts/config.gridsearch.output.yaml && rm BFTBrain/scripts/config.gridsearch.output.yaml"
    ]).check_returncode()

    single_config_path = args.config
    if single_config_path == '':
        single_config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../config/config.framework.yaml")
    print(f"Using config file {single_config_path}")

    subprocess.run([
        "bash",
        "-c",
        f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{master_node_hostname} <<< 'put -r \"{single_config_path}\" BFTBrain/scripts/config.gridsearch.output.yaml'"
    ]).check_returncode()


@miyuki_log('Starting Single Run on Master Node ...')
def start_single(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname
    
    tmux_window_name = f"single-{int(time.time())}"

    if args.public:
        command = f"cd BFTBrain/scripts && ./run_miyuki.sh {args.protocol}"
    else:
        command = f"cd BFTBrain/scripts && ./run_miyuki.sh {args.protocol} --"

    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        f"tmux new-session -d -s {tmux_window_name} && " +
        f"tmux send-keys -t {tmux_window_name}:0 \"{command}\" C-m"
    ]).check_returncode()
    
    print(f"EXPERIMENT HAS STARTED, PLEASE CHECK tmux SESSION '{tmux_window_name}' ON MASTER NODE FOR PROGRESS: {master_node_hostname}")
    print("Type 'stop' to stop the trial.\n$ ", end='')
    
    while input().lower() != 'stop':
        print("'stop' not detected, continue running ...\n$ ", end='')
    
    print("Stopping the trial ...")
    
    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        f"tmux send-keys -t {tmux_window_name}:0 C-d" # send CTRL+D to stop the trial
    ]).check_returncode()


@miyuki_log('Packing Workspace Code ...')
def sync_pack():
    project_base_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../..")
    project_folder_name = os.path.basename(os.path.abspath((os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))))
    
    subprocess.run([
        "bash",
        "-c",
        f"cd {project_base_dir} && " +
        "echo 'abc' > BFTBrain-Sync.tar.gz && rm BFTBrain-Sync.tar.gz && " +
        f"tar -czvf BFTBrain-Sync.tar.gz --transform \"s/^{project_folder_name}/BFTBrain/\" {project_folder_name}"
    ]).check_returncode()


@miyuki_log('Syncing Code to Master Node ...')
def sync_master(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname

    project_base_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../..")
    
    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        # back up servers.txt
        f"cp BFTBrain/scripts/servers.txt servers.bak.txt && " +
        # delete previous BFTBrain folder
        "mkdir -p BFTBrain && rm -rf BFTBrain && " +
        # delete previous BFTBrain-Sync.tar.gz
        "echo 'abc' > BFTBrain-Sync.tar.gz && rm BFTBrain-Sync.tar.gz"
    ]).check_returncode()
    
    subprocess.run([
        "bash",
        "-c",
        f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{master_node_hostname} <<< 'put \"{project_base_dir}/BFTBrain-Sync.tar.gz\" BFTBrain-Sync.tar.gz'"
    ]).check_returncode()
    
    subprocess.run([
        "ssh",
        f"{os.environ['USER']}@{master_node_hostname}",
        "-i",
        private_key_path,
        "-p",
        "22",
        "-o",
        "StrictHostKeyChecking no",
        # extract BFTBrain-Sync.tar.gz
        "tar -xzvf BFTBrain-Sync.tar.gz && " +
        # restore servers.txt
        "mv servers.bak.txt BFTBrain/scripts/servers.txt && " +
        # configure permission
        "chmod 600 BFTBrain/scripts/miyuki/id_cloudlab"
    ]).check_returncode()


@miyuki_log('Syncing Code to Worker Nodes ...')
def sync_workers(workers):
    ks = list(workers.nodes.keys())
    ks.sort()

    worker_nodes = [workers.nodes[k] for k in ks]
    
    with multi.Pool(processes=len(worker_nodes)) as pool:
        pool.map(sync_single_worker, worker_nodes)


def sync_single_worker(worker_node):
    try:
        project_base_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../..")
        
        subprocess.run(
            [
                "ssh",
                f"{os.environ['USER']}@{worker_node.hostname}",
                "-i",
                private_key_path,
                "-p",
                "22",
                "-o",
                "StrictHostKeyChecking no",
                # delete previous BFTBrain folder
                "mkdir -p BFTBrain && rm -rf BFTBrain &> sync.log && " +
                # delete previous BFTBrain-Sync.tar.gz
                "echo 'abc' > BFTBrain-Sync.tar.gz && rm BFTBrain-Sync.tar.gz &> sync.log"
            ],
            stdout = subprocess.DEVNULL,
            stderr = subprocess.DEVNULL
        ).check_returncode()
        
        subprocess.run(
            [
                "bash",
                "-c",
                f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{worker_node.hostname} <<< 'put \"{project_base_dir}/BFTBrain-Sync.tar.gz\" BFTBrain-Sync.tar.gz' &> sync.log"
            ],
            stdout = subprocess.DEVNULL,
            stderr = subprocess.DEVNULL
        ).check_returncode()

        subprocess.run(
            [
                "ssh",
                f"{os.environ['USER']}@{worker_node.hostname}",
                "-i",
                private_key_path,
                "-p",
                "22",
                "-o",
                "StrictHostKeyChecking no",
                # extract BFTBrain-Sync.tar.gz
                "tar -xzvf BFTBrain-Sync.tar.gz &> sync.log && " +
                # compile
                "cd BFTBrain/code && mvn clean &> sync.log && " +
                "mvn package &> sync.log && " +
                "mvn dependency:copy-dependencies &> sync.log"
            ], 
            stdout = subprocess.DEVNULL,
            stderr = subprocess.DEVNULL
        ).check_returncode()
        print('\x1b[6;30;42m' + f"Successfully synced on {worker_node.hostname}" + '\x1b[0m')
    except Exception as e:
        print(f"Error occurred on {worker_node.hostname}, check ~/sync.log on that host for more information")
        raise e


@miyuki_log('Collecting Results from Master Node ...')
def collect_data(master):
    mk = list(master.nodes.keys())[0]
    master_node_hostname = master.nodes[mk].hostname
    
    try:
        subprocess.run([
            "ssh",
            f"{os.environ['USER']}@{master_node_hostname}",
            "-i",
            private_key_path,
            "-p",
            "22",
            "-o",
            "StrictHostKeyChecking no",
            "cd BFTBrain/scripts && " +
            "mkdir -p archieve && rm -r archieve && " +
            "python3 analyze.py &> analyze.log && " +
            "cd .. && " + 
            f"echo 'abc' > {args.experiment}.tar.gz && rm {args.experiment}.tar.gz && tar -czvf {args.experiment}.tar.gz scripts && " +
            "cd scripts && " +
            "python3 clean_all_results.py"
        ]).check_returncode()
    except Exception as e:
        print(f"Error occurred on {master_node_hostname}, check ~/BFTBrain/scripts/analyze.log on that host for more information")
        raise e
    
    subprocess.run([
        "bash",
        "-c",
        f"sftp -oPort=22 -oStrictHostKeyChecking=no -oIdentityFile=\"{private_key_path}\" {os.environ['USER']}@{master_node_hostname} <<< 'get \"BFTBrain/{args.experiment}.tar.gz\"'"
    ]).check_returncode()


if __name__ == '__main__':
    if args.action != 'terminate':
        master_nodes = get_master()
        worker_nodes = get_workers()
    else:
        terminate_master()
        terminate_workers()

    if args.action == 'deploy':
        servers_list_txt = deploy_workers(worker_nodes)
        deploy_master(master_nodes, servers_list_txt)
    elif args.action == 'gridsearch':
        upload_gridsearch_config(master_nodes)
        start_gridsearch(master_nodes)
    elif args.action == 'reproduce':
        upload_reproduction_configs(master_nodes)
        start_reproduction(master_nodes)
    elif args.action == 'single':
        upload_single_config(master_nodes)
        start_single(master_nodes)
    elif args.action == 'sync':
        sync_pack()
        sync_master(master_nodes)
        sync_workers(worker_nodes)
    elif args.action == 'collect':
        collect_data(master_nodes)

