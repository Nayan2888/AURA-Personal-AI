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

## ExecuTorch training boundary

The Android training bridge is being prepared around ExecuTorch 1.4.0's experimental `TrainingModule`/optimizer APIs. Those APIs can execute an ahead-of-time forward/backward graph, expose named gradients, and apply optimizer updates in memory.

However, the current official ExecuTorch Android training API does **not** provide native serialization of newly updated model weights. That means AURA cannot yet turn a successful in-memory training session into a persistent LiteRT-LM LoRA adapter without an explicit weight-export/conversion layer.

## Artifact format boundary

AURA inference consumes a LiteRT-LM `.litertlm` base model and, when supplied, a separate LoRA adapter artifact accepted by LiteRT-LM. The training bridge uses a separate ExecuTorch training `.pte` artifact.

Therefore these are distinct artifacts and must not be treated as interchangeable:

`inference .litertlm` + `LiteRT-LM LoRA adapter`
vs.
`training .pte` + `ExecuTorch tokenizer/dataset/training graph`

The missing bridge is:

`ExecuTorch training weights -> LiteRT-LM-compatible LoRA adapter`

AURA will only register a trained adapter after this conversion produces a real artifact that passes structural/runtime validation.

## Why the trainer remains separate

LiteRT-LM currently provides runtime LoRA loading, but the Android Kotlin API used by AURA is not itself a complete on-device fine-tuning/training API.

The upstream ExecuTorch workflow also treats PTE fine-tuning as experimental and uses separate training graphs/models from inference artifacts. AURA therefore will not invent a training implementation by treating inference-time adapter loading as training.

## Next implementation gate

Before registering a concrete trainer, AURA must verify all of the following on an actual supported device/model:

1. Real gradient/parameter-update execution for the selected base model.
2. Real tokenizer + dataset plumbing for the selected training model.
3. Real export of updated weights after training.
4. Real conversion into a LiteRT-LM-compatible LoRA adapter artifact.
5. Runtime load of that adapter through LiteRT-LM.
6. Memory, thermal, battery, and training-time behavior.
7. Deterministic failure/cancellation handling.
8. Evaluation of the produced adapter against the base model before any activation.
9. Explicit approval before an adapter can become active.

Until those conditions are met, candidate adapters remain separate from the active model and no automatic model replacement is allowed.
