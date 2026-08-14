# Deployment Model

## Initial deployment

- Excitel router provides network access.
- Linux virtual machine runs under VMware.
- VMware networking uses bridged mode.
- NEXA runs from the Linux VM.
- PostgreSQL may run locally for development and controlled deployment.
- Firebase services provide mobile push transport.
- Android client runs on an authorized operator device.

The design must not assume the router can run NEXA code.

## Environment separation

Development, test, and production-like environments should use separate credentials and configuration.
