"""
Error definitions for the ARP observation engine.
"""


class ARPObserverError(Exception):
    """Base exception for all ARP observation errors."""


class InterfaceUnavailableError(ARPObserverError):
    """Raised when the specified network interface cannot be bound."""


class NexaPrivilegeError(ARPObserverError):
    """Raised when the process lacks sufficient capabilities (e.g., CAP_NET_RAW)."""


class ARPTargetOutOfBoundsError(ARPObserverError):
    """Raised when an ARP target falls outside the authorized NetworkScope."""


class TransportTimeoutError(ARPObserverError):
    """Raised when the transport layer entirely fails to execute an interaction."""


class GlobalTimeoutExceededError(ARPObserverError):
    """Raised when the ARP scan exceeds the maximum global deadline."""
