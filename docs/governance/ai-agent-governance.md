# AI Agent Governance

NEXA uses AI coding agents as implementation assistants.

## Approved workflow

```text
Human-approved task
 -> AI implementation
 -> AI self-verification
 -> automated validation
 -> human review
 -> merge
```

## Agent permissions

Agents may:

- inspect repository state
- implement approved work
- add tests
- update documentation
- run validation commands

Agents may not silently:

- redefine architecture
- weaken security controls
- introduce secrets
- remove tests to make CI pass
- change scope without approval
- disable security checks
- claim verification without actually running it

## Required final report

Every coding-agent task must report:

- files changed
- design decisions
- tests executed
- validation results
- security checks
- known limitations
- follow-up risks

## Agent switching

Gemini, Claude Code, or another approved agent may be used for a task without changing the project architecture. The task specification remains the source of truth.

## Prohibitions

## AI agents must not execute destructive or irreversible commands unless explicitly authorized by the human project owner for that exact command and purpose.

That should cover much more than git clean -fd.

## Explicitly prohibited without direct authorization

git clean
git reset --hard
git checkout -- .
git restore .
git restore --staged
git rm -rf
rm -rf
rmdir /s
del /s
Remove-Item -Recurse -Force
format
diskpart
mkfs
dd
shred

## And destructive commands involving:

Docker volumes
Docker system prune
databases
PostgreSQL DROP/DATABASE/TRUNCATE
filesystem-wide deletion
VM disks/snapshots
cloud resources
Firebase projects
GitHub repositories
branches/tags/releases
package lockfiles or source trees through forceful cleanup

## The exact list should be treated as examples, not an exhaustive allowlist. The actual governance rule should be based on the destructive effect.

## Also prohibit dangerous command chaining

The agent should not construct commands that combine destructive behavior with shell chaining or redirection merely to bypass review, for example:

command1 && destructive-command
command1; destructive-command
destructive-command | ...

unless that operation was explicitly authorized.

Safe alternative

The agent should prefer:

inspect
diff
status
copy
move
rename
non-destructive cleanup

and report what it needs removed rather than silently deleting it.

This is especially important because an AI agent has access to the whole repository and can make a catastrophic mistake much faster than a human.
