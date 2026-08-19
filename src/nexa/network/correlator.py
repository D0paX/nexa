"""
Observation Correlator engine.

Processes DeviceObservations and correlates them into DeviceRecords based
on MAC addresses within a bound ScanContext.
"""

import uuid
from ipaddress import IPv4Address, IPv4Network
from typing import Callable, Dict, List, Set

from nexa.domain.correlation import (
    ConflictClassification,
    ObservationConflict,
    PresenceState,
)
from nexa.domain.device import DeviceRecord, ScanContext
from nexa.domain.observation import DeviceObservation
from nexa.domain.scope import NetworkScope


class ObservationCorrelator:
    """
    Correlates DeviceObservations into DeviceRecords in-memory.

    Correlation state is session-scoped and ephemeral. Durable retention and
    pruning semantics are deferred to Phase 1E.
    """

    def __init__(
        self,
        id_factory: Callable[[], uuid.UUID] | None = None,
        initial_records: List[DeviceRecord] | None = None,
    ) -> None:
        """
        Initialize the correlator.

        Args:
            id_factory: Injectable factory for generating opaque device IDs.
                        Defaults to uuid.uuid4.
            initial_records: Optional list of records to hydrate state from persistence.
        """
        self._id_factory = id_factory or uuid.uuid4
        # State: device_id -> DeviceRecord
        self._records: Dict[uuid.UUID, DeviceRecord] = {}
        if initial_records:
            for record in initial_records:
                self._records[record.device_id] = record

    def correlate(
        self, context: ScanContext, observations: List[DeviceObservation]
    ) -> List[DeviceRecord]:
        """
        Correlate new observations within the given context.

        Args:
            context: The ScanContext defining the scope boundary and current run.
            observations: List of new DeviceObservations.

        Returns:
            The updated list of DeviceRecords for the given scope.
        """
        # Reconstruct IPv4Network for membership testing
        scope_net = IPv4Network(
            f"{context.network_scope.network_address}/{context.network_scope.prefix_length}",
            strict=False,
        )

        # Pass 1: normalize and validate observations
        valid_obs = [obs for obs in observations if obs.ipv4_address in scope_net]

        # Pass 2: group/correlate by MAC within the current NetworkScope
        mac_to_obs: Dict[str, List[DeviceObservation]] = {}
        for obs in valid_obs:
            mac_to_obs.setdefault(obs.mac_address, []).append(obs)

        # Pass 3: derive conflicts from grouped observations
        # IP_COLLISION: same IP used by different MACs in this batch
        ip_to_macs: Dict[IPv4Address, Set[str]] = {}
        for obs in valid_obs:
            ip_to_macs.setdefault(obs.ipv4_address, set()).add(obs.mac_address)

        conflicts: List[ObservationConflict] = []
        for ip, macs in sorted(ip_to_macs.items(), key=lambda x: x[0]):
            if len(macs) > 1:
                obs_time = min(o.observed_at for o in valid_obs if o.ipv4_address == ip)
                conflicts.append(
                    ObservationConflict(
                        classification=ConflictClassification.IP_COLLISION,
                        description=f"Multiple MACs observed for IP {ip}",
                        involved_macs=frozenset(macs),
                        observed_at=obs_time,
                    )
                )

        current_macs_in_scope: Set[str] = set()

        # Sort MACs to ensure deterministic ID assignment
        for mac in sorted(mac_to_obs.keys()):
            obs_list = mac_to_obs[mac]
            current_macs_in_scope.add(mac)
            ipv4_set = frozenset(o.ipv4_address for o in obs_list)
            earliest = min(o.observed_at for o in obs_list)
            latest = max(o.observed_at for o in obs_list)

            existing_record = self._find_record(mac, context.network_scope)
            relevant_conflicts = frozenset(
                c for c in conflicts if mac in c.involved_macs
            )

            if existing_record:
                merged_ips = existing_record.ipv4_addresses | ipv4_set
                merged_conflicts = existing_record.conflicts | relevant_conflicts
                first_obs = min(existing_record.first_observed_at, earliest)
                last_obs = max(existing_record.last_observed_at, latest)

                updated = DeviceRecord(
                    device_id=existing_record.device_id,
                    network_scope=context.network_scope,
                    mac_addresses=existing_record.mac_addresses,
                    ipv4_addresses=merged_ips,
                    first_observed_at=first_obs,
                    last_observed_at=last_obs,
                    presence_state=PresenceState.PRESENT,
                    conflicts=merged_conflicts,
                )
                self._records[updated.device_id] = updated
            else:
                new_id = self._id_factory()
                new_record = DeviceRecord(
                    device_id=new_id,
                    network_scope=context.network_scope,
                    mac_addresses=frozenset([mac]),
                    ipv4_addresses=ipv4_set,
                    first_observed_at=earliest,
                    last_observed_at=latest,
                    presence_state=PresenceState.PRESENT,
                    conflicts=relevant_conflicts,
                )
                self._records[new_id] = new_record

        # Pass 4: assign UNSEEN state for previously known records
        # in this scope not seen now
        for record_id, record in list(self._records.items()):
            if record.network_scope == context.network_scope:
                if not any(
                    mac in current_macs_in_scope for mac in record.mac_addresses
                ):
                    unseen_record = DeviceRecord(
                        device_id=record.device_id,
                        network_scope=record.network_scope,
                        mac_addresses=record.mac_addresses,
                        ipv4_addresses=record.ipv4_addresses,
                        first_observed_at=record.first_observed_at,
                        last_observed_at=record.last_observed_at,
                        presence_state=PresenceState.UNSEEN,
                        conflicts=record.conflicts,
                    )
                    self._records[record_id] = unseen_record

        return [
            r
            for r in self._records.values()
            if r.network_scope == context.network_scope
        ]

    def _find_record(self, mac: str, scope: NetworkScope) -> DeviceRecord | None:
        """Find an existing DeviceRecord by MAC within a specific NetworkScope."""
        # Using sorted order to ensure determinism if multiple match (should not happen)
        for record in sorted(self._records.values(), key=lambda r: r.device_id):
            if record.network_scope == scope and mac in record.mac_addresses:
                return record
        return None
