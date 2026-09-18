# AURA Personal AI

AURA is a private, offline-first, action-oriented personal AI assistant designed for Nayan Rajgor.

## Core principles

- **Private by default:** AURA is intended for one personal user.
- **Offline-first:** Core chat and stored knowledge should work without an internet connection whenever the required local model and data are available.
- **Honest execution:** No fake success, fabricated sources, simulated tests, or placeholder capabilities presented as real.
- **Permissioned actions:** Sensitive actions require explicit confirmation and use a default-deny policy.
- **Learn, evaluate, then upgrade:** AURA may retain useful experiences, build training examples, evaluate candidate improvements, and only switch versions after passing safety and quality gates.
- **Recoverable evolution:** Model and skill upgrades must support versioning, audit history, rollback, and user approval.

## Development status

**Phase 0 — Foundation specification:** Started.

Implementation will proceed in verified milestones. Each milestone must document:

1. Scope and acceptance criteria
2. Files changed
3. Tests executed and their real results
4. Known limitations
5. Security review notes

## Planned high-level components

- Local inference runtime
- Conversation orchestrator
- Memory and knowledge store
- Experience and feedback logger
- Skill registry and skill execution engine
- Research provider with explicit network controls
- Training data builder
- On-device or delegated adapter-training engine
- Evaluation harness
- Model/skill version manager
- Approval gate and rollback manager
- Audit log

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/SECURITY.md`](docs/SECURITY.md).
