# AURA Architecture

## 1. Product boundary

AURA is a private personal assistant. It is separate from the Nayan AI/Gemini project and must not share code, credentials, data, or deployment assumptions without an explicit decision.

## 2. Architectural rules

- Use clear module boundaries and dependency inversion.
- Keep the local inference path functional without network access.
- Treat network research as an optional, observable capability.
- Separate knowledge retrieval from executable actions.
- Make every action carry a risk classification and authorization decision.
- Store model, skill, prompt, and knowledge versions independently.
- Make state transitions auditable and reversible.

## 3. Logical modules

### Core

- `orchestrator`: receives requests and coordinates planning, retrieval, action, and response.
- `domain`: stable domain models, policies, result types, and error contracts.
- `security`: authorization, confirmation, sandbox boundaries, and audit events.

### Intelligence

- `inference`: local model loading, streaming generation, cancellation, and resource limits.
- `memory`: conversation memory, durable facts, embeddings/indexing, and retention policy.
- `knowledge`: verified learned procedures, source metadata, confidence, and expiry.
- `skills`: discoverable skills with declared inputs, outputs, permissions, and version.

### Learning lifecycle

- `experience`: records attempts, outcomes, user feedback, and failure explanations.
- `dataset`: converts approved experiences into training/evaluation examples.
- `training`: executes supported adapter or specialist-model training jobs.
- `evaluation`: measures quality, regression, safety, latency, and resource use.
- `versions`: manages candidates, approval, activation, and rollback.

### Integrations

- `research`: HTTPS research provider with explicit opt-in network policy.
- `storage`: encrypted local persistence and optional user-controlled backup.
- `platform`: Android permissions, connectivity, battery, storage, and background limits.

## 4. Request lifecycle

1. Validate and classify the request.
2. Determine whether local knowledge and skills are sufficient.
3. If permitted and necessary, perform bounded research.
4. Produce a plan with declared actions and risk levels.
5. Request confirmation for sensitive or irreversible actions.
6. Execute inside a constrained capability boundary.
7. Verify the result instead of assuming success.
8. Record the experience and user feedback.
9. Queue learning only when data consent and quality requirements are met.
10. Evaluate candidate improvements before any activation.

## 5. Learning and upgrade lifecycle

`Experience → Review → Dataset → Candidate Training → Evaluation → Approval Gate → Activation → Monitoring → Rollback if needed`

A candidate must never replace the active version merely because it was trained successfully. Activation requires measurable evaluation results, safety checks, compatibility checks, and explicit approval where configured.

## 6. Initial implementation order

1. Repository and engineering contract
2. Domain result/error models
3. Security and permission policy
4. Local storage abstraction
5. Orchestrator skeleton
6. Inference abstraction with a deterministic fake only for unit tests
7. Real local runtime integration
8. Memory and experience persistence
9. Skill contract and safe execution boundary
10. Research provider
11. Evaluation and version management

No later phase should be marked complete without evidence from tests or device validation.
