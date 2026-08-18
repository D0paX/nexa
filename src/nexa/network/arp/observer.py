"""
ARP Observation Engine orchestrator.

Manages batches, timeouts, retries, and deduplication for read-only network observation.
"""

import time
from typing import List, Set, Tuple

from nexa.domain.observation import DeviceObservation
from nexa.domain.scope import InvalidNetworkScopeError, NetworkScope
from nexa.network.arp.errors import GlobalTimeoutExceededError
from nexa.network.arp.normalization import ARPResponseNormalizer
from nexa.network.arp.targets import ARPTargetGenerator
from nexa.network.arp.transport import ScapyTransportAdapter


class ARPObserver:
    """
    Orchestrates bounded ARP discovery over a local network scope.
    """

    def __init__(
        self,
        transport: ScapyTransportAdapter | None = None,
        batch_size: int = 64,
        global_timeout_seconds: float = 60.0,
        inter_batch_delay: float = 0.1,
    ) -> None:
        """
        Initialize the observer.

        Args:
            transport: The adapter for raw packet injection/capture.
            batch_size: Maximum targets per batch.
            global_timeout_seconds: Hard deadline for the entire scan.
            inter_batch_delay: Delay between batches to prevent floods.
        """
        self._transport = transport or ScapyTransportAdapter()
        self._batch_size = batch_size
        self._global_timeout = global_timeout_seconds
        self._inter_batch_delay = inter_batch_delay

    def observe(self, scope: NetworkScope) -> List[DeviceObservation]:
        """
        Executes a bounded ARP sweep of the specified network scope.

        Args:
            scope: The validated boundaries for observation.

        Returns:
            A deduplicated list of DeviceObservations.

        Raises:
            InvalidNetworkScopeError: If scope is invalid.
            GlobalTimeoutExceededError: If the scan exceeds the maximum deadline.
            NexaPrivilegeError: If privileges are insufficient.
            InterfaceUnavailableError: If the interface is missing.
        """
        if scope.prefix_length < 16:
            raise InvalidNetworkScopeError("Scope exceeds maximum allowed size (/16).")

        start_time = time.monotonic()
        deadline = start_time + self._global_timeout

        generator = ARPTargetGenerator(scope)
        all_targets = list(generator.generate())

        # Deduplication tracking: (IPv4Address, canonical_mac)
        seen_pairs: Set[Tuple[str, str]] = set()
        final_observations: List[DeviceObservation] = []

        # Process in batches
        for i in range(0, len(all_targets), self._batch_size):
            if time.monotonic() > deadline:
                raise GlobalTimeoutExceededError(
                    f"Scan exceeded global timeout of {self._global_timeout}s."
                )

            batch = all_targets[i : i + self._batch_size]

            # Initial attempt
            raw_responses = self._transport.send_arp_requests(
                batch, interface_name=scope.interface_name, timeout=2.0
            )

            # Identify unanswered targets for retry
            responded_ips = {r.get("ip") for r in raw_responses if r.get("ip")}
            unanswered = [ip for ip in batch if str(ip) not in responded_ips]

            # Retry unanswered only (maximum 1 retry)
            if unanswered:
                retry_responses = self._transport.send_arp_requests(
                    unanswered, interface_name=scope.interface_name, timeout=2.0
                )
                raw_responses.extend(retry_responses)

            # Normalize responses
            observations = ARPResponseNormalizer.normalize(
                raw_responses, interface_name=scope.interface_name
            )

            # Deduplicate by (IP, MAC) exactly as requested
            for obs in observations:
                key = (str(obs.ipv4_address), obs.mac_address)
                if key not in seen_pairs:
                    seen_pairs.add(key)
                    final_observations.append(obs)

            # Throttle between batches if there are more batches
            if i + self._batch_size < len(all_targets):
                time.sleep(self._inter_batch_delay)

        return final_observations
