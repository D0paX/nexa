# ADR-007: Linux Network Discovery Mechanism

## Status

Accepted

## Context

Phase 1A requires discovering the current network environment (interfaces, IPv4 addresses, subnet masks, default gateway). The project mandates strong boundaries, specifically avoiding unnecessary external dependencies and prohibiting network manipulation. 

Retrieving default gateway and network masks purely through Python's standard library is complex cross-platform and requires parsing files like `/proc/net/route` on Linux or using undocumented raw socket `ioctl` calls. While third-party libraries like `netifaces` or `psutil` provide this data cleanly, they introduce native C dependencies, which increases the build footprint and violates the "avoid dependencies where the standard library/OS is sufficient" constraint.

NEXA targets Linux VMs (e.g., VMware bridged environments). The modern, standard mechanism for querying network state on Linux is the `iproute2` suite (`ip` command).

## Decision

We will use the system `ip` command (`iproute2`) via safe Python subprocess calls to perform Phase 1A network discovery.

Specifically, we will invoke:
- `ip -j -4 route show default` (to find the primary default interface)
- `ip -j -4 addr show` (to discover IP configurations and subnet masks)

## Subprocess Safety Constraints

To mitigate command injection and execution risks, the implementation MUST adhere to:
- `shell=False`: Never interpolate user strings or environment variables into shell evaluations.
- **Fixed Executables & Arguments**: Use explicit lists (e.g., `['ip', '-j', '-4', 'addr', 'show']`).
- **Timeouts**: Enforce bounded execution (e.g., `timeout=2.0` seconds).
- **Read-Only**: Use commands that merely `show` or list state; never `add`, `del`, or `flush`.
- **JSON Parsing**: Prefer `-j` for structured JSON output to avoid fragile text-scraping.

## Alternatives Considered

1. **Python `netifaces` or `psutil` packages:**
   - *Rejected.* Introduces external dependencies for basic data query that the OS already exposes. Violates the minimal-dependency mandate for Phase 1.
2. **Parsing `/proc/net/route` and `/proc/net/dev` directly:**
   - *Rejected.* Requires manual hex decoding of IP addresses and does not cleanly provide CIDR prefixes without complex bitmask parsing.
3. **Python `socket.ioctl` calls:**
   - *Rejected.* Cryptic, platform-specific, and difficult to maintain compared to standard OS tooling.

## Security Considerations

- **Command Injection:** Eliminated by strict `shell=False` and no dynamic argument assembly.
- **Privilege Assumptions:** Running `ip addr show` and `ip route show` does not require root/`CAP_NET_ADMIN` privileges.
- **Information Leakage:** Output parsing is strictly normalized into a domain model before being logged. Raw command output is not persisted.

## Portability Boundary

This mechanism is inherently Linux-specific. The architecture must place this code within a specific infrastructure adapter (e.g., `LinuxNetworkEnvironment`) that implements a generic network discovery interface. This allows future injection of different adapters for macOS/Windows if those platforms become targets.

## Operational Considerations

The system relies on the presence of `iproute2`, which is standard on virtually all modern Linux distributions (Debian, Ubuntu, Alpine, RHEL). If `ip` is missing, the adapter will catch the `FileNotFoundError` and return a structured discovery error rather than crashing the application.
