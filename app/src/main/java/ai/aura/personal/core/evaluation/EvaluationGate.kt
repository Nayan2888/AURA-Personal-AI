package ai.aura.personal.core.evaluation

object EvaluationGate {

    fun check(
        report: EvaluationReport,
        approvalGranted: Boolean,
        maxQualityRegression: Double = 0.0
    ): Decision {
        require(maxQualityRegression >= 0.0) {
            "Maximum quality regression must not be negative"
        }

        if (!approvalGranted) return Decision.NOT_APPROVED
        if (!report.safetyChecksPassed) return Decision.SAFETY_FAILED
        if (!report.compatibilityChecksPassed) return Decision.INCOMPATIBLE

        val allowedLoss = report.baseMeanLoss + maxQualityRegression
        if (report.candidateMeanLoss > allowedLoss) {
            return Decision.QUALITY_REGRESSION
        }

        return Decision.PASSED
    }

    enum class Decision {
        PASSED,
        NOT_APPROVED,
        SAFETY_FAILED,
        INCOMPATIBLE,
        QUALITY_REGRESSION
    }
}
