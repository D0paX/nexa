# Deployment

## Initial target

NEXA runs on a Linux VM using VMware bridged networking so the VM can participate directly in the monitored LAN.

## Deployment requirements

- supported Python runtime
- network interface access appropriate to discovery
- protected credentials
- persistent storage where configured
- controlled outbound access for cloud notification services

Deployment steps must be reproducible from a clean environment and documented before release.
