# AdaptiveBedrock

## Mode 1 - No switching

This mode is useful for testing a _fixed_ protocol inside our protocol pool. To enable this mode:

* Change the `config.framework.yaml` file (or any corresponding framework config file) as below. 
  * Set `general.learning` to be `false`.
  * Set `benchmark.checkpoint-size` to be a very large value, e.g., `1000000000`.


## Mode 2 - Switching in pre-defined order

This mode is useful for testing _purely_ the switching part, i.e., without the learning component. To enable this mode:

* Change the `config.framework.yaml` file (or any corresponding framework config file) as below. 
  * Set `general.learning` to be `false`.
  * Set `benchmark.checkpoint-size` to be a reasonable value, e.g., `5000`, `1000`, or `500`. This represents the epoch length in terms of blocks.
  * Set `switching.debug-sequence` to be a pre-defined sequence that you want to use.


* If running BFTBrain on a single machine, run 
  ```bash
  cd BFTBrain/scripts/ && ./local_exp.sh pbft
  ``` 
  Here PBFT will be used for the first epoch. Then it switches protocols using the pre-defined sequence in a round-robin fashion.


* If running BFTBrain distributedly on CloudLab, simply use the miyuki automation tool. See the deployment section below.

## Mode 3 - Switching via learning agent

This mode should be used when running final evaluations of the end-to-end BFTBrain system, where the decision of next protocol is given by the decentralized learning agent. To enable this mode:

* Change the `config.framework.yaml` file (or any corresponding framework config file) as below. 
  * Set `general.learning` to be `true`.
  * Set `benchmark.checkpoint-size` to be a reasonable value.
  * Set `general.report-sequence` to be a reasonable value, e.g., `0.5 * benchmark.checkpoint-size`. Set `general.exchange-sequence` to be a reasonable value larger than the report sequence, e.g., `0.6 * benchmark.checkpoint-size`.
  * Remove the field `switching.debug-sequence`.

* If running BFTBrain on a single machine, run 
  ```bash
  cd BFTBrain/scripts/ && ./local_exp.sh pbft learning
  ``` 
  Here PBFT will be used for the first epoch, before the learning agent kicks in.

  The learning agents are created in a seperate tmux session. You can attach to the session using `tmux attach -t cloudlab-learning`.


* If running BFTBrain distributedly on CloudLab, simply use the miyuki automation tool. See the deployment section below.

## Deployment and Experiments

### Miyuki Automation Tool Overview

Miyuki can be used on any machine to instantiate Cloudlab instances and launch experiments.

Due to legacy issues, each experiment consist of a `master` node, i.e. `controller`, and some `workers` node. They are two different Cloudlab experiments.

It is recommended here to yet launch another machine on Cloudlab to run Miyuki instead of running locally, as the network connection is more stable there.

Overall Usage:

```bash
$ ./scripts/miyuki/main.py --help
usage: main.py [-h] --experiment EXPERIMENT --profile PROFILE [--project PROJECT] {deploy,gridsearch,reproduce,single,collect,terminate,sync}

Start a BFTBrain experiment.

positional arguments:
  {deploy,gridsearch,reproduce,single,collect,terminate,sync}
                        Action to perform.
    deploy              Start a new experiment
    gridsearch          Run a grid search (do not include `deploy` as a first step)
    reproduce           Reproduce a single experiment (do not include `deploy` as a first step)
    single              Run a single configuration (do not include `deploy` as a first step)
    collect             Collect results from a single experiment (do not include `deploy` as a first step)
    terminate           Terminate a cloudlab experiment
    sync                Sync the code to all nodes in the experiment (including master and workers)

options:
  -h, --help            show this help message and exit
  --experiment EXPERIMENT, -e EXPERIMENT
                        Cloudlab experiment name
  --profile PROFILE, -p PROFILE
                        Cloudlab profile to use
  --profile2 PROFILE2, -p2 PROFILE2
                        Second Cloudlab profile to use (optional)
  --project PROJECT, -j PROJECT
                        Cloudlab project to use

---

$ ./scripts/miyuki/main.py deploy --help
usage: main.py deploy [-h] [--no-instantiate]

options:
  -h, --help            show this help message and exit
  --no-instantiate, -n  Skip instantiating the experiment (default False,
                        i.e. instantiate the experiment). This option can be
                        used when the experiment is manually instantiated on
                        Cloudlab but not yet have the environment setup.

---

$ ./scripts/miyuki/main.py gridsearch --help
usage: main.py gridsearch [-h] [--public] base_config grid_config

positional arguments:
  base_config   Base configuration file to use
  grid_config   Gridsearch configuration file to use

options:
  -h, --help    show this help message and exit
  --public, -u  Use public IPs for the experiment (default False, i.e. use local IPs)

---

$ scripts/miyuki/main.py reproduce --help
usage: main.py reproduce [-h] [--public] [--trial TRIAL] [--duration DURATION] configs_path

positional arguments:
  configs_path          Path to the directory containing the configurations to reproduce

options:
  -h, --help    show this help message and exit
  --public, -u  Use public IPs for the experiment (default False, i.e. use local IPs)
  --trial TRIAL, -t TRIAL    number of trials to perform for each config (default 10)
  --duration DURATION, -d DURATION        duration of each trial (sec) (default: 300)

---

$ ./scripts/miyuki/main.py single --help
usage: main.py single [-h] [--public] [--config CONFIG] protocol

positional arguments:
  protocol              Protocol profile name to run (e.g. pbft)

options:
  -h, --help            show this help message and exit
  --public, -u          Use public IPs for the experiment (default False, i.e. use local IPs)
  --config CONFIG, -c CONFIG
                        Path to the configuration to run, default is to use 
                        `code/config.framework.yaml` on this machine

---

$ ./scripts/miyuki/main.py collect --help
usage: main.py collect [-h]

options:
  -h, --help  show this help message and exit

---

$ scripts/miyuki/main.py terminate --help
usage: main.py terminate [-h]

options:
  -h, --help  show this help message and exit

---

$ ./scripts/miyuki/main.py sync --help
usage: main.py sync [-h]

options:
  -h, --help  show this help message and exit
```

Note that environment variables should also be set properly for Miyuki to work. See examples in later sections.

### Setup

0. Run locally or instantiate another machine on Cloudlab, login.
1. Preparation for any task issuer machine [RUNNING ONCE IS ENOUGH]: Clone repo, adjust permission for private key, install python3-pip, install python dependencies
   ```bash
   # clone repository
   git clone https://github.com/JeffersonQin/BFTBrain
   # setup script
   ./BFTBrain/scripts/miyuki/setup.sh
   ```

### Deploy

Issue deployment task via Miyuki. Usage:

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> deploy
```

More examples:

```bash
# m510 instance with f=1
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -p m510-f-1 -e <EXPERIMENT_NAME> deploy
# xl170 instance with f=1
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -p xl170-f-1 -e <EXPERIMENT_NAME> deploy
# c6525-25g instance with f=1
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -p c6525-25g-f-1 -e <EXPERIMENT_NAME> deploy
```

### Grid Search

Grid search a deployed instance through miyuki. Usage:

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> gridsearch <BASE_CONFIG> <GRID_SEARCH_CONFIG>
```

### Reproduce

Reproduce all configs in a folder on a deployed instance through miyuki. Usage:

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> reproduce <CONFIGS_PATH>
```

### Run Single Config Once

Run single protocol once, interactive mode, stop when user types stop.

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> single <PROTOCOL_NAME> --config <CONFIG_PATH>
```

### Experiment Data Analysis and Collection

Analyze data and collect results via Miyuki. Usage:

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> collect
```

### Termination

Terminate instances via Miyuki. Usage:

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> terminate
```

### Sync

Sync the file on this machine to both master and workers.

```bash
set +o history && USER='USER_NAME' PWORD='PASSWORD' ./BFTBrain/scripts/miyuki/main.py -e <EXPERIMENT_NAME> -p <PROFILE_NAME> sync
```

### Notes

* `scripts/miyuki/cloudlab.pem`: See https://gitlab.flux.utah.edu/powder-profiles/powder-control/-/tree/master
  > You'll also need to download your Powder credentials. You'll find a button to do so in the drop-down menu accessed by clicking on your username after logging into the Powder portal. This will download a file called `cloudlab.pem`, which you will need later.
* `scripts/miyuki/id_cloudlab`: Private key for Cloudlab SSH
* Known issues:
  * There might be some bug regarding fetching the logfile per machine.
  * If there is bug using `single`, it is always a good idea to use `reproduce` by only including one single configuration file and then specify the trial to be 1 and some specific duration. `single` might not be useful for debugging, but is especially useful when you want to monitor the result instantly and do quick experiment to see if the things work.

### Credits and LICENSE

Cloudlab API from https://gitlab.flux.utah.edu/powder-profiles/powder-control/-/tree/master/

`scripts/miyuki` part will follow AGPL-3.0 License

## Two-site WAN Experiment

* Use Miyuki's `--profile2` (`-p2`) parameter to specify the second profile.
* Remember to change the generated `BFTBrain/scripts/servers.txt` on master node to make sure
  * Coordinator, client, and the fast nodes are on the same cluster. By convention, the first line should be the coordinator, the last line should be the client. **Important: THE FILE SHOULD NOT CONTAIN A TRAILING NEW LINE CHARACTER!!!**
  * Here is an example config:
    ```
    xxxxxx@node-0.2-sitew.bft-evaluation-PG0.utah.cloudlab.us
    xxxxxx@node-1.2-sitew.bft-evaluation-PG0.utah.cloudlab.us
    xxxxxx@node-2.2-sitew.bft-evaluation-PG0.utah.cloudlab.us
    xxxxxx@node-0.2-sitew2.bft-evaluation-PG0.wisc.cloudlab.us
    xxxxxx@node-1.2-sitew2.bft-evaluation-PG0.wisc.cloudlab.us
    xxxxxx@node-3.2-sitew.bft-evaluation-PG0.utah.cloudlab.us
    ```
* Make sure to use Miyuki's `--public` (`-u`) parameter to start any experiment!!!
