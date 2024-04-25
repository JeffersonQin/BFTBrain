"""Cluster profile of xl170 instances with f=4, latency=50ms, bandwidth=1Gbps

Instructions:
check BFTBrain's `README.md`
"""

#
# NOTE: This code was machine converted. An actual human would not
#       write code like this!
# Edited by Haoyun Qin

fault_count = 4
hardware_type = "xl170"
# Latency is in milliseconds
latency = 50
# BW is in Kbps
bandwidth = 1000000

node_count = 3 * fault_count + 1 + 2

# Import the Portal object.
import geni.portal as portal

# Import the ProtoGENI library.
import geni.rspec.pg as pg

# Import the Emulab specific extensions.
import geni.rspec.emulab as emulab

# Create a portal object,
pc = portal.Context()

# Create a Request object to start building the RSpec.
request = pc.makeRequestRSpec()

ifaces = []

for i in range(node_count):
    node = request.RawPC("node-" + str(i))
    node.hardware_type = hardware_type
    iface = node.addInterface("interface-" + str(i))
    ifaces.append(iface)

# Link link-1
link_1 = request.LAN()

for iface in ifaces:
    link_1.addInterface(iface)

# BW is in Kbps
link_1.bandwidth = bandwidth
# Latency is in milliseconds
# RTT will be latency * 4
# Machine 1 ----> switch ----> Machine 2
#           <----        <----
# single round will be latency * 2
link_1.latency = latency // 2

# Print the generated rspec
pc.printRequestRSpec(request)
