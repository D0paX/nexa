# NEXA Documentation Manifest

This manifest is the Phase 0 baseline. It intentionally lists the authoritative documentation set so future work can detect missing governance artifacts.

## Root documents

- `CHANGELOG.md`
- `CODE_OF_CONDUCT.md`
- `CONTRIBUTING.md`
- `README.md`
- `SECURITY.md`

## Documentation tree

- `docs/README.md`
- `docs/adr/ADR-001-project-runtime.md`
- `docs/adr/ADR-002-service-boundaries.md`
- `docs/adr/ADR-003-device-identity.md`
- `docs/adr/ADR-004-cryptographic-trust-model.md`
- `docs/adr/ADR-005-event-architecture.md`
- `docs/adr/ADR-006-notification-architecture.md`
- `docs/adr/ADR-007-linux-network-discovery-mechanism.md`
- `docs/adr/ADR-008-network-scope-model.md`
- `docs/adr/README.md`
- `docs/architecture/component-architecture.md`
- `docs/architecture/data-flow.md`
- `docs/architecture/deployment-model.md`
- `docs/architecture/quality-attributes.md`
- `docs/architecture/system-overview.md`
- `docs/development/coding-standards.md`
- `docs/development/definition-of-done.md`
- `docs/development/development-guide.md`
- `docs/development/documentation-governance.md`
- `docs/development/repository-conventions.md`
- `docs/development/testing-strategy.md`
- `docs/governance/ai-agent-governance.md`
- `docs/governance/change-management.md`
- `docs/governance/decision-log.md`
- `docs/governance/dependency-policy.md`
- `docs/governance/engineering-governance.md`
- `docs/governance/risk-register.md`
- `docs/operations/backup-and-recovery.md`
- `docs/operations/configuration.md`
- `docs/operations/deployment.md`
- `docs/operations/logging.md`
- `docs/operations/observability.md`
- `docs/operations/runbooks.md`
- `docs/product/future-feature-process.md`
- `docs/product/product-requirements.md`
- `docs/product/product-vision.md`
- `docs/product/scope-and-non-goals.md`
- `docs/release/deprecation-and-compatibility.md`
- `docs/release/release-process.md`
- `docs/release/versioning.md`
- `docs/roadmap/master-roadmap.md`
- `docs/roadmap/phase-1-network-visibility.md`
- `docs/roadmap/phase-gates.md`
- `docs/roadmap/roadmap-change-policy.md`
- `docs/security/credential-lifecycle.md`
- `docs/security/cryptography.md`
- `docs/security/privacy-and-data-classification.md`
- `docs/security/secrets-management.md`
- `docs/security/security-boundaries.md`
- `docs/security/security-testing.md`
- `docs/security/supply-chain-security.md`
- `docs/security/threat-model.md`
- `docs/security/trust-model.md`
- `docs/security/vulnerability-management.md`
- `docs/templates/adr-template.md`
- `docs/templates/agent-task-template.md`
- `docs/templates/feature-spec.md`
- `docs/templates/phase-template.md`
- `docs/templates/risk-template.md`
- `docs/templates/roadmap-change-template.md`
- `docs/templates/runbook-template.md`
- `docs/testing/security-test-plan.md`
- `docs/testing/test-environment.md`
- `docs/ui/accessibility.md`
- `docs/ui/design-principles.md`

## Completeness rule

Do not create ad-hoc governance documents during feature development when an existing document or template can be extended. Add a new document only when a distinct source of truth is required.

Any future addition to the documentation set must also update this manifest and explain why the existing documentation could not represent the new information.
