#!/bin/bash
# if provided any arguments, then get local ip, not public ip
if [ $# -gt 0 ]; then
    local="true"
else
    local="false"
fi

# get the list of network interfaces with IP addresses
ip addr | grep inet | awk '{print $2}' | cut -d'/' -f1 | sort -u > /tmp/ip_addresses.txt

# use a loop to check each IP address and determine if it belongs to the local network
while read ip_address; do
    if [[ $ip_address =~ ^(10\.|172\.(1[6-9]|2[0-9]|3[0-1])\.|192\.168\.) ]]; then
        if [[ $local == "true" ]]; then
            # found a local IP address
            echo "$ip_address"
            break
        fi
    elif [[ $ip_address == *":"* ]]; then
        continue # ignore ipv6
    elif [[ $ip_address == "127.0.0.1" ]]; then
        continue # ignore localhost
    else
        # found a public IP address
        echo "$ip_address"
        break
    fi
done < /tmp/ip_addresses.txt

# clean up temporary files
rm /tmp/ip_addresses.txt
