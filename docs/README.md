# NEXA Documentation System

This directory is the source of truth for NEXA's engineering documentation.

## Documentation rules

- Architecture changes require an ADR.
- Security-model changes require a security review and usually an ADR.
- Public behavior changes require requirements and test updates.
- New phases require a phase specification before implementation.
- New features should start from a feature specification.
- Operational changes require updated runbooks or operations documentation.
- Deprecated behavior must be documented before removal.
- Documents should link to authoritative related documents instead of duplicating conflicting rules.

## Directory map

```text
docs/
├── architecture/
├── adr/
├── development/
├── governance/
├── product/
├── requirements/
├── roadmap/
├── security/
├── operations/
├── testing/
├── release/
├── ui/
└── templates/
```

## Change control

Documentation may evolve without an ADR for editorial corrections. Any change that alters a technical decision, security assumption, interface contract, scope boundary, or operational requirement must use the appropriate governed change process.
