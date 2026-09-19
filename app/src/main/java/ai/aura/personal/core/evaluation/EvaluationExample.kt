package ai.aura.personal.core.evaluation

data class EvaluationExample(
    val id: String,
    val input: String,
    val expectedOutput: String
) {
    init {
        require(id.isNotBlank()) { "Evaluation example id must not be blank" }
        require(input.isNotBlank()) { "Evaluation input must not be blank" }
        require(expectedOutput.isNotBlank()) {
            "Evaluation expected output must not be blank"
        }
    }
}
