# Contributing to NEXA

NEXA follows a governed, test-first engineering workflow.

## Before changing code

1. Read the relevant architecture and security documents.
2. Check whether an existing ADR governs the area.
3. Define the intended behavior and acceptance criteria.
4. Identify security and operational implications.
5. Keep the change narrowly scoped.

## Implementation

- Follow the repository coding standards.
- Prefer existing dependencies and abstractions.
- Do not introduce secrets.
- Do not silently alter architectural boundaries.
- Add or update tests with behavior changes.
- Update documentation when behavior, interfaces, configuration, or architecture changes.

## Validation

A change is complete only when the required checks pass and the change is reviewable.

See `docs/development/testing-strategy.md` and `docs/development/definition-of-done.md`.

## AI-assisted development

AI coding agents may implement approved work but do not own architectural decisions.

See `docs/governance/ai-agent-governance.md`.
