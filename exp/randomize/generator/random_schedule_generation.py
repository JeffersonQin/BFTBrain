import yaml
import os
import os.path as osp
import random
import numpy as np

# --------------------------------------------------------------
# Configuration starts here


sample_interval = 1
total_duration = 1200
small_sample_range = 0.1
os.makedirs(osp.join(os.path.dirname(os.path.abspath(__file__)), "../configs"), exist_ok=True)
configs = [("base-config-ff.yaml", "../configs/ff.yaml"), ("base-config-id.yaml", "../configs/id.yaml")]

feature_range = {
    "workload.payload.request-size": [
        ["normal", 0, 120000, 4000, 2000], # [0, 120k] => mu = 4k, sigma = 2k
        ["normal", 0, 120000, 100000, 20000], # [0, 120k] => mu = 100k, sigma = 20k
        ["normal", 0, 120000, 4000, 2000], # [0, 120k] => mu = 4k, sigma = 2k
    ],
    # min, max, mean, sigma  
    "fault.slow-proposal.timer": [
        ["uniform", 0, 0], # constant 0
        ["uniform", 0, 0], # constant 0
        ["normal", 0, 120, 20, 10], # [0, 120] => mu = 20ms, sigma = 10ms
    ]
}


# Configuration ends here
# --------------------------------------------------------------

def get_field(config, field):
    fields = field.split(".")
    current = config
    for f in fields:
        current = current[f]
    return current

class Dumper(yaml.Dumper):
    def increase_indent(self, flow=False, *args, **kwargs):
        return super().increase_indent(flow=flow, indentless=False)

for base_config, out_config in configs:

    base_config = osp.join(os.path.dirname(os.path.abspath(__file__)), base_config)
    out_config = osp.join(os.path.dirname(os.path.abspath(__file__)), out_config)

    with open(base_config, 'r') as f:
        config = yaml.load(f, Loader=yaml.Loader)


    for feature, f_ranges in feature_range.items():
        
        features = []
        
        for f_range in f_ranges:
            if f_range[0] == 'uniform-two':
                lower1 = f_range[1]
                upper1 = f_range[2]
                lower2 = f_range[3]
                upper2 = f_range[4]
            elif f_range[0] == 'normal':
                lower = f_range[1]
                upper = f_range[2]
                mean = f_range[3]
                sigma = f_range[4]
                random_samples = np.random.normal(mean, sigma, total_duration // sample_interval)         
            elif f_range[0] == 'uniform':
                lower = f_range[1]
                upper = f_range[2]

            
            for i in range(total_duration // sample_interval):
                if f_range[0] == 'uniform-two':
                    if random.random() < 0.5:
                        upper = upper1
                        lower = lower1
                    else:
                        upper = upper2
                        lower = lower2
                    random_sample = random.randint(lower, upper)
                elif f_range[0] == 'uniform':
                    random_sample = random.randint(lower, upper)
                elif f_range[0] == 'normal':
                    random_sample = int(random_samples[i])
                
                features.append(max(lower, min(upper, random_sample)))
            
        intervals = [sample_interval * 1000] * (len(features) - 1)
        
        print("num_features: ", len(features))
        print("num_intervals: ", len(intervals))
        
        get_field(config, feature)["schedule"] = {
            "values": features,
            "intervals": intervals
        }

    with open(out_config, "w") as f:
        yaml.dump(config, f, default_flow_style=False, Dumper=Dumper)
