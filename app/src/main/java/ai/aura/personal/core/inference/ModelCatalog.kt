package ai.aura.personal.core.inference

internal data class DownloadableModel(val title: String, val file: String, val url: String, val sha256: String, val bytes: Long)

internal val localModelCatalog = listOf(
    DownloadableModel("Qwen3 0.6B · 4-bit", "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8768ea43e6d801ee0d353e5036007e6790a2ba05/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0", 343490560),
    DownloadableModel("Qwen3 0.6B", "Qwen3-0.6B.litertlm",
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8768ea43e6d801ee0d353e5036007e6790a2ba05/Qwen3-0.6B.litertlm",
        "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108", 613722264)
)
