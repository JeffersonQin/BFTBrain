import os

from matplotlib import pyplot as plt
import numpy as np

# import pandas as pd
import re
import subprocess

num_stats = 5
# because hotstuff's bug hasn't yet been fixed
skip_zero_analysis = ["hotstuff"]


def get_throughput(report: str):
    throughput_pattern = r"throughput\s+(\d+\.\d+|\d+)req/s"
    throughputs = []
    for match in re.findall(throughput_pattern, report):
        throughputs.append(float(match))
    return throughputs


def archieve_benchmark(benchmark_name: str, aname: str):
    os.makedirs(f"archieve/{aname}", exist_ok=True)
    os.symlink(
        f"../../{benchmark_name}",
        f"archieve/{aname}/{benchmark_name}",
    )


def should_skip(name: str) -> bool:
    for skip_name in skip_zero_analysis:
        if skip_name in name:
            return True
    return False


benchmark_archieves = [
    file_name for file_name in os.listdir(".") if file_name.endswith(".tar.gz")
]

for archieve in benchmark_archieves:
    os.makedirs(archieve[:-7], exist_ok=True)
    subprocess.run(["tar", "-xzvf", archieve, "-C", archieve[:-7]])

for archieve in benchmark_archieves:
    benchmark_name = archieve[:-7]
    print("Analyzing Benchmark-", benchmark_name)
    benchmark_base_dir = f"{benchmark_name}/code/benchmarks"

    try:
        benchmark_files = os.listdir(benchmark_base_dir)
    except Exception as e:
        print(f"Error occurred on finding {benchmark_base_dir}, {repr(e)}")
        continue

    best_protocol = ""
    protocol_stat = -1

    is_zero = False
    is_small_throughput = False

    inf = {}

    for file in benchmark_files:
        if file[-4:] != ".txt":
            continue
        benchmark_file = os.path.join(benchmark_base_dir, file)
        with open(benchmark_file, "r") as f:
            report = f.read()
        throughputs = get_throughput(report)

        print(f"{file}: {throughputs}")

        plt.plot(np.arange(len(throughputs)), np.array(throughputs), label=file)

        num_stats_ = num_stats
        if num_stats > len(throughputs):
            num_stats_ = len(throughputs)

        if num_stats_ == 0:
            if not should_skip(file):
                is_zero = True
            continue

        avg_throughput = sum(throughputs[-num_stats_:]) / num_stats_
        if avg_throughput > protocol_stat:
            best_protocol = file
            protocol_stat = avg_throughput

        protocol_name = file[5:-4]
        inf[protocol_name] = avg_throughput

        if not should_skip(file):
            if throughputs[-1] == 0:
                is_zero = True
                continue

    # largest throughput is less than 1000 req/s
    if protocol_stat < 1000:
        is_small_throughput = True

    plt.legend()
    plt.savefig(f"{benchmark_name}/plot.pdf")
    plt.clf()

    print(
        f"Benchmark {benchmark_name}: BEST {best_protocol} with {protocol_stat} req/s"
    )

    if is_zero:
        archieve_benchmark(benchmark_name, "zero")
        continue
    if is_small_throughput:
        archieve_benchmark(benchmark_name, "small")
        continue

    # sort inf
    inf = sorted(inf.items(), key=lambda x: x[1], reverse=True)
    cat_name = ""
    for pname, _ in inf:
        cat_name += pname + "_"
    cat_name = cat_name[:-1]
    archieve_benchmark(benchmark_name, cat_name)
