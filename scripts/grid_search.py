import yaml
import subprocess
import copy
import itertools
import sys


with open("config.gridsearch.yaml", "r") as f:
    grid_search_config = yaml.safe_load(f)

grid_search = list(itertools.product(*grid_search_config.values()))
grid_search_params = []

for item in grid_search:
    t = {}
    for i, k in enumerate(grid_search_config.keys()):
        t[list(grid_search_config.keys())[i]] = item[i]
    grid_search_params.append(t)

with open("config.to.adjust.yaml", "r") as f:
    base_config = yaml.safe_load(f)

if len(sys.argv) > 1 and sys.argv[1] == "--local":
    local = True
else:
    local = False


class Dumper(yaml.Dumper):
    def increase_indent(self, flow=False, *args, **kwargs):
        return super().increase_indent(flow=flow, indentless=False)


for params in grid_search_params:
    config = copy.deepcopy(base_config)

    for k, v in params.items():
        keys = k.split(".")
        t = config
        for key in keys[:-1]:
            t = t[key]
        t[keys[-1]] = v

    with open("config.gridsearch.output.yaml", "w") as f:
        yaml.dump(config, f, default_flow_style=False, Dumper=Dumper)

    if local:
        subprocess.run(["bash", "./run.sh", "30", "--"])
    else:
        subprocess.run(["bash", "./run.sh", "30"])
