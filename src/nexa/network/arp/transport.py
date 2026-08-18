"""
Scapy transport adapter for ARP observation.

This module provides an infrastructure boundary that isolates Scapy from the
rest of the application. Domain models must never see Scapy types.
"""

import errno
from ipaddress import IPv4Address
from typing import Dict, List

try:
    from scapy.all import ARP, Ether, conf, srp  # type: ignore

    # Configure Scapy for cleaner behavior
    conf.verb = 0
except ImportError:
    # Handle gracefully during testing/audit if Scapy is missing
    ARP = Ether = srp = conf = None  # type: ignore

from nexa.network.arp.errors import (
    InterfaceUnavailableError,
    NexaPrivilegeError,
    TransportTimeoutError,
)


class ScapyTransportAdapter:
    """
    Adapter for sending and receiving raw ARP packets via Scapy.
    """

    def send_arp_requests(
        self, targets: List[IPv4Address], interface_name: str, timeout: float = 2.0
    ) -> List[Dict[str, str]]:
        """
        Sends ARP who-has requests to a list of targets and collects responses.

        Args:
            targets: List of IPv4Address targets to query.
            interface_name: The network interface to bind to.
            timeout: Timeout in seconds for waiting for responses.

        Returns:
            List of dictionaries containing 'ip' and 'mac' strings.

        Raises:
            NexaPrivilegeError: If the process lacks raw socket privileges.
            InterfaceUnavailableError: If the interface cannot be found or bound.
            TransportTimeoutError: If the transport mechanism fails to execute.
        """
        if not targets:
            return []

        if ARP is None:
            raise RuntimeError("Scapy is not installed or importable.")

        target_ips = [str(ip) for ip in targets]

        # We broadcast to the standard FF:FF:FF:FF:FF:FF MAC
        # The ARP target is a list of IPs (Scapy can take a list)
        packet = Ether(dst="ff:ff:ff:ff:ff:ff") / ARP(pdst=target_ips)

        try:
            # Send and receive packets at layer 2
            # srp returns a tuple (answered, unanswered)
            ans, _ = srp(packet, iface=interface_name, timeout=timeout, verbose=0)
        except PermissionError as e:
            raise NexaPrivilegeError(
                "Insufficient privileges for raw socket access. "
                "Ensure the process has CAP_NET_RAW capabilities."
            ) from e
        except OSError as e:
            # Scapy raises OSError(No such device) if interface doesn't exist
            if "No such device" in str(e):
                raise InterfaceUnavailableError(
                    f"Interface {interface_name} is unavailable."
                ) from e
            if e.errno == errno.EPERM or e.errno == errno.EACCES:
                raise NexaPrivilegeError(
                    "Insufficient privileges for raw socket access."
                ) from e
            # Reraise other unexpected OS errors
            raise TransportTimeoutError(f"Transport execution failed: {e}") from e
        except Exception as e:
            raise TransportTimeoutError(f"Unexpected transport failure: {e}") from e

        results = []
        for _sent, received in ans:
            # We explicitly pull out the string representation of IP and MAC
            # to prevent Scapy objects from leaking to the caller.
            try:
                ip_val = str(received.psrc)
                mac_val = str(received.hwsrc)
                if ip_val and mac_val:
                    results.append({"ip": ip_val, "mac": mac_val})
            except AttributeError:
                # If a malformed response lacks these fields, skip it.
                continue

        return results
