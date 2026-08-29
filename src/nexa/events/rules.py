from typing import Union

from nexa.domain.events import AggregatedSecurityEvent, SecurityEvent, Severity


class RuleEngine:
    """
    Evaluates SecurityEvents to determine if they warrant an Alert.
    """

    def evaluate(self, event: Union[SecurityEvent, AggregatedSecurityEvent]) -> bool:
        """
        Returns True if the event should be promoted to an Alert.
        Currently, any event with Severity >= LOW creates an Alert.
        INFO events are just logged/discarded.
        """
        if event.severity in (
            Severity.LOW,
            Severity.MEDIUM,
            Severity.HIGH,
            Severity.CRITICAL,
        ):
            return True
        return False
