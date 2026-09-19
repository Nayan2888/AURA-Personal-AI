package ai.aura.personal.core.versions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActivationApprovalStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun approvalCanBeSavedAndReloaded() {
        val root = temporaryFolder.newFolder("approvals")
        val approval = ActivationApproval(
            id = "approval-1",
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            grantedAtEpochMs = 100L
        )

        ActivationApprovalStore(root).save(approval)
        val reloaded = ActivationApprovalStore(root).get("approval-1")

        assertNotNull(reloaded)
        assertEquals(approval, reloaded)
    }

    @Test
    fun duplicateApprovalIdsAreRejected() {
        val root = temporaryFolder.newFolder("approvals")
        val store = ActivationApprovalStore(root)
        val approval = ActivationApproval(
            id = "approval-1",
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            grantedAtEpochMs = 100L
        )

        store.save(approval)
        val error = runCatching { store.save(approval) }.exceptionOrNull()

        assertEquals("Activation approval already exists: approval-1", error?.message)
    }
}
