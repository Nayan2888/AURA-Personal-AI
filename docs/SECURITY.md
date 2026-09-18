# AURA Security Contract

This document is an implementation gate, not a claim that all controls are already implemented.

## Mandatory rules

1. **Default deny:** Unknown, sensitive, destructive, external, or irreversible actions are denied until explicitly authorized.
2. **No implicit elevation:** A model response cannot grant itself permissions.
3. **Least privilege:** Skills declare the minimum capabilities they need.
4. **Confirmation:** Sensitive actions require a clear user confirmation immediately before execution.
5. **No secret exposure:** API keys, tokens, private files, and personal data must not be included in prompts, logs, telemetry, or training examples unless explicitly permitted and appropriately redacted.
6. **Sandboxing:** File and process operations must enforce canonical path boundaries, reject path traversal and symlink escapes, and block protected locations.
7. **Network controls:** Network access is disabled unless the user and platform policy allow it. All external requests use HTTPS and bounded timeouts.
8. **Result verification:** An action is not considered successful solely because a tool returned without throwing an error.
9. **Auditability:** Authorization decisions, action attempts, outcomes, and version changes must be recorded without sensitive payload leakage.
10. **Rollback:** Model and skill activation must be reversible.

## Risk levels

- `SAFE`: Read-only, local, reversible operations with low impact.
- `REVIEW`: Operations that may affect user data, external services, or significant resources; require policy checks and often confirmation.
- `SENSITIVE`: Financial, account, communication, deletion, security, credential, or irreversible operations; explicit confirmation is mandatory.
- `BLOCKED`: Operations prohibited by policy, unavailable permissions, unsafe scope, or failed validation.

## Learning safety

- Do not train on secrets, credentials, private messages, or personal documents by default.
- Keep source provenance and consent metadata with each learning example.
- Separate training data from evaluation data.
- Reject examples containing prompt injection, malicious instructions, or unverified outcomes.
- Require regression and safety evaluation before candidate activation.
- Preserve the previous active version until the candidate passes all gates.

## Verification requirements

Every security-sensitive implementation must include tests for:

- Default-deny behavior
- Confirmation bypass attempts
- Path traversal and symlink escape
- Network-disabled behavior
- Secret redaction
- Failed and partially successful tool execution
- Rollback after failed candidate activation
