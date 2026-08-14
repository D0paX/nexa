# Development Guide

## Environment

NEXA currently targets Python 3.12.

The development environment should be reproducible using the repository's declared dependency and tooling configuration.

## Workflow

```text
Requirement
 -> design
 -> implementation
 -> tests
 -> validation
 -> review
 -> merge
```

## Rules

- keep changes scoped
- avoid speculative abstractions
- do not bypass existing boundaries without an approved decision
- document externally visible behavior
