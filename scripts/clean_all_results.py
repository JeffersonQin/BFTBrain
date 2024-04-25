# delete all results under `./BFTBrain/scripts`
import os
import subprocess

benchmark_archieves = [
    file_name for file_name in os.listdir(".") if file_name.endswith(".tar.gz")
]

for archieve in benchmark_archieves:
    subprocess.run(["rm", "-r", f"{archieve[:-7]}"])
    subprocess.run(["rm", archieve])
