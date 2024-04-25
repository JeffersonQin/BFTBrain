"""Cluster profile of xl170(4)+c6420(2) instances with f=1, two sites. Bandwidth=10Gbps.

This profile create lan that spans two clusters. Note that you must a bandwidth on your lan for this to work.

Instructions:
Click on any node in the topology and choose the `shell` menu item. When your shell window appears, use `ping` to test the link."""

# Import the Portal object.
import geni.portal as portal
# Import the ProtoGENI library.
import geni.rspec.pg as pg

# Create a portal context.
pc = portal.Context()

# Create a Request object to start building the RSpec.
request = pc.makeRequestRSpec()

# Variable number of nodes at two sites.
pc.defineParameter("X", "Number of Nodes at Site One", portal.ParameterType.INTEGER, 4)
pc.defineParameter("Y", "Number of Nodes at Site Two", portal.ParameterType.INTEGER, 2)

hardware_type_1 = "xl170"
hardware_type_2 = "c6420"
# BW is in Kbps
bandwidth = 10000000

# Retrieve the values the user specifies during instantiation.
params = pc.bindParameters()

# Check parameter validity.
if params.X < 2:
    pc.reportError(portal.ParameterError("You must choose at least 2 nodes at Site One"))
if params.Y < 2:
    pc.reportError(portal.ParameterError("You must choose at least 2 nodes at Site Two"))

# Count for node name.
counter = 0;

# ifaces. 
ifaces = []

# Nodes at Site One.
for i in range(params.X):
    node = request.RawPC("node" + str(counter))
    node.hardware_type = hardware_type_1
    # Assign to Site One.
    node.Site("Site1")
    # Create iface and assign IP
    iface = node.addInterface("eth1")
    # Specify the IPv4 address
    iface.addAddress(pg.IPv4Address("192.168.1." + str(counter + 1), "255.255.255.0"))
    ifaces.append(iface)
    counter = counter + 1
    pass

# Nodes at Site Two
for i in range(params.Y):
    node = request.RawPC("node" + str(counter))
    node.hardware_type = hardware_type_2
    # Assign to Site Two
    node.Site("Site2")
    # Create iface and assign IP
    iface = node.addInterface("eth1")
    # Specify the IPv4 address
    iface.addAddress(pg.IPv4Address("192.168.1." + str(counter + 1), "255.255.255.0"))
    # And add to the lan.
    ifaces.append(iface)
    counter = counter + 1
    pass

# Now add the link to the rspec. 
lan = request.LAN("lan")

# BW is in Kbps
lan.bandwidth = bandwidth

# Add interfaces to lan
for iface in ifaces:
    lan.addInterface(iface)

# Print the RSpec to the enclosing page.
pc.printRequestRSpec(request)
