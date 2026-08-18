"""
Normalization logic for ARP observations.

Translates raw dictionaries from the transport layer into pure DeviceObservation models.
"""

from datetime import datetime, timezone
from ipaddress import AddressValueError, IPv4Address
from typing import Dict, List

from nexa.domain.observation import DeviceObservation, InvalidObservationError


class ARPResponseNormalizer:
    """
    Normalizes raw ARP transport results into domain models.
    """

    @staticmethod
    def normalize(
        raw_responses: List[Dict[str, str]], interface_name: str
    ) -> List[DeviceObservation]:
        """
        Converts raw dictionaries to DeviceObservation objects.

        Malformed records are skipped rather than failing the entire batch.

        Args:
            raw_responses: List of dicts with 'ip' and 'mac'.
            interface_name: The interface the responses were observed on.

        Returns:
            List of valid DeviceObservation models.
        """
        observations = []
        now = datetime.now(timezone.utc)

        for item in raw_responses:
            ip_str = item.get("ip")
            mac_str = item.get("mac")

            if not ip_str or not mac_str:
                continue

            try:
                ip_addr = IPv4Address(ip_str)
                obs = DeviceObservation(
                    ipv4_address=ip_addr,
                    mac_address=mac_str,
                    observed_at=now,
                    interface_name=interface_name,
                    source="arp_discovery",
                )
                observations.append(obs)
            except (AddressValueError, InvalidObservationError):
                # We skip malformed individual responses.
                # In a real system, these might be logged for diagnostic purposes.
                continue

        return observations
