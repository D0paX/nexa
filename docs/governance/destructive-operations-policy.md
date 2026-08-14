# NEXA Destructive Operations Policy

**Status:** Accepted
**Authority:** NEXA Engineering Governance
**Scope:** All AI coding agents, automation agents, scripts, CI jobs, and development tooling operating on the NEXA project or its associated development infrastructure.

---

## 1. Purpose

NEXA uses AI agents to assist with implementation, testing, documentation, and engineering operations.

AI agents can execute commands significantly faster than a human can review them. A seemingly convenient cleanup command can permanently destroy source code, Git history, databases, credentials, generated artifacts, or development environments.

This policy establishes a mandatory safety boundary:

> **No AI agent has implicit permission to perform destructive or irreversible operations.**

The existence of a repository problem, dirty working tree, build failure, test failure, configuration conflict, or implementation inconvenience does not constitute authorization to destroy data.

---

## 2. Core Principle

AI agents must preserve existing user work.

An agent must assume that:

- existing files are valuable
- uncommitted changes are valuable
- existing configuration is intentional unless proven otherwise
- Git history is valuable
- local databases may contain valuable development state
- generated files may contain information required by another workflow
- credentials and environment configuration may be intentionally present

The agent must never decide that existing work is disposable merely because it interferes with the requested task.

---

## 3. Destructive Operations

A destructive operation is any operation that can permanently delete, overwrite, reset, invalidate, rewrite, or irreversibly alter data, source code, repository history, environments, credentials, infrastructure, or security state.

This definition is intentionally broader than a fixed command blacklist.

### Examples include

#### Git

```text
git clean
git clean -fd
git clean -fdx
git reset --hard
git checkout -- .
git restore .
git restore --staged
git rm -rf
git branch -D
git tag -d
git rebase
git filter-repo
git push --force
git push --force-with-lease
```

Some commands may be safe in particular circumstances, but AI agents must not assume that they are safe.

#### Filesystem

```text
rm -rf
rmdir /s
Remove-Item -Recurse -Force
del /s
format
shred
disk formatting operations
recursive force deletion
bulk overwrite operations
```

Equivalent commands in other shells, scripts, languages, or tools are covered by this policy.

#### Databases

Examples include:

```text
DROP DATABASE
DROP TABLE
TRUNCATE
bulk destructive migrations
irreversible schema rewrites
```

#### Containers and virtual machines

Examples include:

```text
docker system prune
docker volume prune
destructive container volume deletion
VM disk deletion
VM snapshot deletion
destructive VM reset operations
```

#### Cloud and external services

Examples include:

- deleting cloud resources
- destroying databases
- deleting Firebase projects
- deleting storage buckets
- deleting secrets
- deleting repositories
- deleting releases
- deleting infrastructure resources

---

## 4. Authorization Requirement

An AI agent may perform a destructive operation only when the human project owner has explicitly authorized **that specific operation for that specific purpose**.

General instructions such as:

> "clean up the project"

> "fix the repository"

> "reset the environment"

> "make it work"

do **not** constitute authorization.

Authorization must be specific enough that the agent knows:

1. what will be changed or destroyed
2. why it is required
3. what scope is affected
4. that the human understands the irreversible consequences

When authorization is ambiguous, the agent must not proceed.

---

## 5. Ask Instead of Destroy

When a destructive action appears necessary, the agent must stop and request authorization.

The agent should report:

### What

The exact operation it believes is necessary.

### Why

Why the operation appears necessary.

### Scope

What files, history, databases, environments, or resources could be affected.

### Risk

Whether the operation is reversible and what could be lost.

### Alternative

Any non-destructive alternative that could accomplish the same goal.

The agent must then wait for explicit authorization.

---

## 6. Pre-Modification Inspection

Before modifying an existing repository, the agent must inspect the current state.

At minimum:

```text
working directory
Git status
existing files
relevant source files
relevant configuration
```

For Git repositories, the agent should inspect:

```text
git status
git branch
git remote -v
```

where applicable.

The purpose is to ensure that existing user work is not mistaken for disposable generated state.

---

## 7. Existing Uncommitted Changes

Uncommitted changes must be treated as user-owned work.

If the agent encounters unexpected modifications:

> **Do not reset them.**

> **Do not clean them.**

> **Do not overwrite them merely to continue.**

The agent must identify the conflicting files and ask the human how to proceed.

---

## 8. No Destructive Cleanup

Agents must not use destructive cleanup simply because it is convenient.

For example, an agent must not:

```text
git clean -fd
```

because untracked files make the repository "messy."

It must not:

```text
git reset --hard
```

because a previous implementation is inconvenient.

It must not delete an environment because recreating it appears easier.

It must not delete test databases because a clean database would simplify testing.

Convenience is not authorization.

---

## 9. No Destructive Workarounds

An agent must not use destructive commands to bypass:

- merge conflicts
- failed tests
- dependency conflicts
- formatting problems
- static-analysis errors
- configuration problems
- build failures
- stale generated files
- environment problems
- architectural inconsistencies

The correct response is to diagnose the problem.

---

## 10. Command Chaining and Indirect Destruction

The policy applies regardless of how a destructive action is executed.

Agents must not bypass this policy through:

- shell aliases
- scripts
- command chaining
- pipes
- redirects
- PowerShell commands
- Python scripts
- Node scripts
- Docker commands
- CI commands
- temporary shell files
- generated automation

For example, placing a destructive command inside a script does not make the operation non-destructive.

The effect matters, not the mechanism.

---

## 11. Broad Repository Writes

Agents should prefer targeted modifications.

Avoid broad repository-wide write operations when a targeted operation is sufficient.

Examples:

Instead of formatting every file unnecessarily:

```text
ruff format .
```

prefer validation:

```text
ruff format --check .
```

and format only intentionally modified files when formatting changes are actually required.

The same principle applies to:

- migrations
- automated rewrites
- dependency updates
- code generation
- file renaming
- configuration transformations

---

## 12. Database Safety

AI agents must assume development databases can contain important state.

Before destructive database operations, the agent must determine:

- database identity
- environment
- affected data
- reversibility
- backup/recovery status

No production-like database may be destroyed or reset without explicit authorization.

Test databases should still not be destroyed automatically merely because they are labeled "test."

---

## 13. Git History Safety

Git history is an engineering asset.

AI agents must not rewrite repository history unless explicitly authorized.

This includes:

- force pushes
- history rewriting
- destructive rebases
- filter operations
- deleting historical branches or tags
- replacing commits with rewritten history

Normal additive commits are preferred.

---

## 14. External Infrastructure Safety

AI agents must not assume that external infrastructure is disposable.

The same authorization requirement applies to:

- GitHub repositories
- branches
- releases
- Actions configuration
- Firebase resources
- cloud databases
- storage
- DNS
- virtual machines
- Docker resources
- secrets
- credentials

A resource being development infrastructure does not automatically make it disposable.

---

## 15. CI/CD Safety

CI jobs must not perform destructive operations against developer environments or external infrastructure unless explicitly designed and authorized for that purpose.

CI must not contain hidden cleanup behavior that can destroy developer work.

Destructive deployment operations, when eventually required, must have:

- explicit scope
- documented authorization
- rollback strategy
- environment protection
- appropriate credentials
- logging

---

## 16. Credentials and Security Material

Never delete, overwrite, rotate, revoke, or replace credentials automatically unless the task explicitly requires it and the operation is authorized.

If an agent detects a potentially leaked credential:

1. stop exposing it
2. avoid copying it into logs
3. report the finding
4. follow the security incident process

Credential response procedures are governed by:

`docs/security/secrets-management.md`

and

`docs/security/vulnerability-management.md`

---

## 17. Recovery and Rollback

When a destructive operation is legitimately authorized, the agent should determine whether recovery is possible before executing it.

Where appropriate, establish:

- backup
- snapshot
- export
- rollback point
- restoration procedure

The existence of a backup does not remove the authorization requirement.

---

## 18. AI Agent Final Verification

Before completing a task, the agent must verify that it did not unintentionally destroy or overwrite existing work.

The final report must include:

- Git status
- files created
- files modified
- files deleted
- commands that affected repository state
- any destructive operation requested or authorized
- remaining risks

If no destructive operations occurred, explicitly state:

> **No destructive or irreversible operations were executed.**

This statement must only be made after actual verification.

---

## 19. Governance Hierarchy

This policy is subordinate only to explicit human authorization.

The execution hierarchy is:

```text
Human Project Owner
        ↓
NEXA Governance
        ↓
Approved ADRs
        ↓
Approved Task
        ↓
AI Agent Implementation
```

An AI agent's own judgment cannot override this policy.

A model's suggestion that a destructive operation is:

- standard
- harmless
- faster
- conventional
- required by a tool
- necessary for a clean build

does not constitute authorization.

---

## 20. Enforcement

Violations must be treated as engineering incidents.

Repeated or deliberate violations may result in:

- task termination
- agent access restriction
- rollback
- additional review requirements
- removal of the affected automation
- security investigation where applicable

The goal is not to make agents incapable of performing useful work.

The goal is to ensure that **speed never outruns reversibility and human control**.

---

## 21. Related Documents

- `docs/governance/ai-agent-governance.md`
- `docs/governance/engineering-governance.md`
- `docs/governance/change-management.md`
- `docs/development/definition-of-done.md`
- `docs/security/secrets-management.md`
- `docs/security/vulnerability-management.md`
- `docs/operations/backup-and-recovery.md`

---

## 22. Policy Rule

The canonical rule for NEXA is:

> **Inspect before modifying. Preserve existing work. Prefer reversible operations. Never destroy project state without explicit human authorization.**
