import yaml
import socket
import subprocess
import ipaddress
import traceback
import sys

with open("config.gridsearch.output.yaml", "r") as f:
    data = yaml.safe_load(f)

with open("servers.txt", "r") as f:
    servers = f.read().strip().splitlines()

servers_ = []

if len(sys.argv) > 1 and sys.argv[1] == "--local":
    local = True
else:
    local = False

for server in servers:
    while True:
        try:
            server_host = str(server).split("@")[1]
            args = [
                "ssh",
                server,
                "-p",
                "22",
                "-o",
                "StrictHostKeyChecking no",
                '"BFTBrain/scripts/get_ip.sh"',
            ]
            if local:
                args.append("--")
            result = subprocess.run(args, stdout=subprocess.PIPE)
            server_host = result.stdout.decode().strip()
            # check whether is valid ipv4
            ipaddress.ip_address(server_host)
            if type(ipaddress.ip_address(server_host)) is not ipaddress.IPv4Address:
                raise Exception("IP IS NOT IPv4")
            servers_.append(server_host)
            print("Detected server:", server_host)
            break
        except Exception as e:
            traceback.print_exc()
            print("retry...")

servers = servers_

data["network"]["server"] = f"{servers[0]}:9020"
data["network"]["units"] = [f"{server}:9020" for server in servers[1:]]

class Dumper(yaml.Dumper):
    def increase_indent(self, flow=False, *args, **kwargs):
        return super().increase_indent(flow=flow, indentless=False)

yaml.dump(data, open("config.framework.yaml", "w"), default_flow_style=False, Dumper=Dumper)
