# AURA On-Device LoRA Backend Status

## Current verified integration

AURA's Android inference layer uses LiteRT-LM 0.17.1. That release exposes a real per-conversation LoRA configuration through `ConversationConfig.loraConfig` and `LoraConfig(loraPath = ...)`. AURA now passes an optional local adapter path to that API on a per-generation basis.

This adapter path is intentionally optional. When no adapter is supplied, inference behavior remains unchanged.

## Training boundary

The current AURA training package does **not** claim that on-device LoRA training is implemented.

The production interface is:

- `OnDeviceLoraTrainingEngine`
- `LoraTrainingSpec`
- `TrainingPreflight`
- `TrainingArtifactStore`

No concrete trainer is registered yet. This is intentional: an adapter must never be reported as trained unless a real training backend has produced it successfully.

## Why the trainer remains separate

LiteRT-LM currently provides runtime LoRA loading, but the Android Kotlin API used by AURA is not itself a complete on-device fine-tuning/training API.

The upstream repository contains LoRA runtime components and test fixtures, while current upstream discussions/issues show active work and compatibility problems around LoRA and on-device fine-tuning. Therefore AURA will not invent a training implementation by treating inference-time adapter loading as training.

## Next implementation gate

Before registering a concrete trainer, AURA must verify all of the following on an actual supported device/model:

1. Real gradient/parameter-update execution for the selected base model.
2. Real LoRA adapter artifact generation in a format accepted by the LiteRT-LM runtime.
3. Memory, thermal, battery, and training-time behavior.
4. Deterministic failure/cancellation handling.
5. Evaluation of the produced adapter against the base model before any activation.
6. Explicit approval before an adapter can become active.

Until those conditions are met, candidate adapters remain separate from the active model and no automatic model replacement is allowed.
