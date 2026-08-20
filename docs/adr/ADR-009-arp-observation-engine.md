# ADR-009: ARP Observation Engine

## Status

Accepted

## Context

NEXA Phase 1C requires an engine to observe devices on the local network. We must establish a mechanism to detect device presence bounded by the canonical `NetworkScope` produced in Phase 1B. We need to define the networking abstraction used, the target generation approach, the timeout/retry policy, and the security boundaries for this active network interaction.

## Decision

We will implement a bounded ARP observation engine using **Scapy** as the packet manipulation library.

Phase 1C is explicitly defined as **read-only network observation using bounded ARP discovery requests.** 

### Allowed:
* ARP `who-has` discovery requests
* Receiving ARP replies
* Normalizing responses

### Forbidden:
* Forged ARP replies
* ARP poisoning
* MITM (Man-in-the-Middle)
* Gateway impersonation
* Traffic interception or modification
* Connection disruption
* Automated mitigation

**Key Policies:**
- **Why Scapy:** Scapy provides mature Python abstractions for constructing and receiving Layer-2 packets and is suitable for NEXA's initial Linux ARP observation implementation. It avoids error-prone manual binary packing via standard library sockets.
- **Privilege Model:** NEXA must never automatically invoke `sudo` or otherwise self-elevate privileges. The deployment environment is responsible for providing raw socket capabilities (e.g., `CAP_NET_RAW` or running as root). If privileges are missing, the engine will explicitly fail.
- **Interface Binding:** The engine strictly binds to the `interface_name` specified in the `NetworkScope`.
- **Target-Scope Enforcement:** Target generation mathematically iterates over valid observation targets from `NetworkScope`, strictly enforcing the `/16` maximum scope limit.
- **Batching:** The engine will send requests in default batches of **64 targets** to maintain bounded network load and predictable memory usage on home networks.
- **Timeout:** A strict **60-second global deadline** applies to the entire scan. The observer will check this deadline between batches and stop immediately if it is exceeded.
- **Retry Semantics:** Retries will explicitly target **only the unanswered hosts** from a batch, rather than indiscriminately resending the full batch.
- **Deduplication:** Observations will be deduplicated by the exact tuple of `(ipv4_address, mac_address)`. A single IP producing multiple MACs is a meaningful anomaly and will not be silently discarded.
- **MAC Normalization:** The domain model (`DeviceObservation`) will validate and store MAC addresses in one strict canonical representation (`aa:bb:cc:dd:ee:ff`).

## Alternatives Considered

1. **Passive Sniffing Only:**
   - *Rejected.* Relies on chatty hosts and delays discovery. Phase 1C authorizes bounded `who-has` requests to establish a baseline.
2. **Deduplication strictly by IP:**
   - *Rejected.* This would silently discard IP collisions or ARP spoofing artifacts on the network. We must observe and record the exact `(IP, MAC)` mappings observed.
3. **Automatically invoking `sudo`:**
   - *Rejected.* Software should not attempt to automatically escalate privileges; this violates the least-privilege operational model.

## Consequences

### Security Implications
- Scapy requires raw socket access (`CAP_NET_RAW`). By explicitly failing when privileges are missing, NEXA avoids unsafe workarounds or hidden escalation vectors.
- Target generation explicitly prevents arbitrary, out-of-scope targets from being scanned.

### Operational Implications
- The 60-second total scan timeout guarantees bounded runtime, even when operating near the `/16` safety limit with slow or non-responsive targets.
- Scapy is introduced as a required runtime dependency (pinned appropriately in `pyproject.toml`).

## Test Strategy
- **Unit Tests:** Mock the Scapy transport layer to verify target generation, batching, the `/16` maximum, 60s global timeout, retry-only-unanswered semantics, IP+MAC deduplication, and privilege failures.
- **Integration Tests:** Opt-in explicitly via `NEXA_RUN_NETWORK_INTEGRATION=1`. The test will target a deliberately small, bounded set (e.g., just the local gateway) rather than a full subnet scan, verifying real Scapy socket execution and response normalization.
