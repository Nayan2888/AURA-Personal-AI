package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.evaluation.EvaluationReport
import ai.aura.personal.core.evaluation.EvaluationReportStore
import ai.aura.personal.core.security.ArtifactDigest
import ai.aura.personal.core.training.TrainingArtifactStore
import ai.aura.personal.core.versions.ActivationApproval
import ai.aura.personal.core.versions.ActivationApprovalStore
import ai.aura.personal.core.versions.ModelActivationCoordinator
import ai.aura.personal.core.versions.ModelVersion
import ai.aura.personal.core.versions.ModelVersionStore
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssistantRuntimeManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun noActiveVersionRunsBaseModelWithoutAdapter() {
        runBlocking {
        val root = temporaryFolder.newFolder("base-only")
        val reports = EvaluationReportStore(root)
        val versions = ModelVersionStore(root)
        val engines = mutableListOf<RecordingEngine>()
        val runtime = AssistantRuntimeManager(versions, reports) { file ->
            RecordingEngine(file).also(engines::add)
        }
        val model = temporaryFolder.newFile("base.litertlm").apply { writeText("base") }

        runtime.load(model)
        val response = runtime.respond(
            history = emptyList(),
            userMessage = userMessage("hello")
        )

        assertEquals("adapter:none", response.content)
        assertNull(runtime.loadedActiveVersionId())
        assertEquals(1, engines.size)
        assertTrue(engines.single().initialized)
        }
    }

    @Test
    fun activeVersionAdapterIsPinnedIntoRuntime() {
        runBlocking {
        val root = temporaryFolder.newFolder("active")
        val reports = EvaluationReportStore(root)
        val versions = ModelVersionStore(root)
        val adapter = publishAndActivate(
            root = root,
            versions = versions,
            reports = reports,
            versionId = "candidate-1",
            baseVersionId = "base-1",
            createdAt = 1L
        )
        val engines = mutableListOf<RecordingEngine>()
        val runtime = AssistantRuntimeManager(versions, reports) { file ->
            RecordingEngine(file).also(engines::add)
        }
        val model = temporaryFolder.newFile("base.litertlm").apply { writeText("base") }

        runtime.load(model)
        val response = runtime.respond(
            history = emptyList(),
            userMessage = userMessage("hello")
        )

        assertEquals("adapter:" + adapter.name, response.content)
        assertEquals("candidate-1", runtime.loadedActiveVersionId())
        assertEquals(adapter, engines.single().lastAdapter)
        }
    }

    @Test
    fun activationChangeAutomaticallyReloadsRuntimeOnNextResponse() {
        runBlocking {
        val root = temporaryFolder.newFolder("switch")
        val reports = EvaluationReportStore(root)
        val versions = ModelVersionStore(root)
        val firstAdapter = publishAndActivate(
            root = root,
            versions = versions,
            reports = reports,
            versionId = "candidate-1",
            baseVersionId = "base-1",
            createdAt = 1L
        )
        val engines = mutableListOf<RecordingEngine>()
        val runtime = AssistantRuntimeManager(versions, reports) { file ->
            RecordingEngine(file).also(engines::add)
        }
        val model = temporaryFolder.newFile("base.litertlm").apply { writeText("base") }

        runtime.load(model)
        runtime.respond(emptyList(), userMessage("first"))

        val secondAdapter = publishAndActivate(
            root = root,
            versions = versions,
            reports = reports,
            versionId = "candidate-2",
            baseVersionId = "base-1",
            createdAt = 2L,
            reportBaseVersionId = "candidate-1"
        )

        val response = runtime.respond(
            history = emptyList(),
            userMessage = userMessage("second")
        )

        assertEquals("adapter:" + secondAdapter.name, response.content)
        assertEquals("candidate-2", runtime.loadedActiveVersionId())
        assertEquals(2, engines.size)
        assertTrue(engines[0].closed)
        assertSame(engines[1], engines.last())
        assertEquals(secondAdapter, engines.last().lastAdapter)
        assertTrue(firstAdapter.exists())
        }
    }

    @Test
    fun closeIsDeferredUntilInFlightGenerationFinishes() {
        runBlocking {
            val root = temporaryFolder.newFolder("close-race")
            val model = temporaryFolder.newFile("base.litertlm").apply {
                writeText("base")
            }
            val blockingEngine = BlockingEngine(model)
            val runtime = AssistantRuntimeManager(
                modelVersionStore = ModelVersionStore(root),
                evaluationReportStore = EvaluationReportStore(root)
            ) {
                blockingEngine
            }

            runtime.load(model)
            val responseJob = launch {
                runtime.respond(
                    history = emptyList(),
                    userMessage = userMessage("hello")
                )
            }

            blockingEngine.generationStarted.await()
            runtime.close()

            assertTrue(!blockingEngine.closed)

            blockingEngine.releaseGeneration.complete(Unit)
            responseJob.join()

            assertTrue(blockingEngine.closed)
        }
    }

    @Test
    fun loadWaitsForInFlightGenerationBeforeReplacingEngine() {
        runBlocking {
            val root = temporaryFolder.newFolder("reload-race")
            val firstModel = temporaryFolder.newFile("first.litertlm").apply {
                writeText("first")
            }
            val secondModel = temporaryFolder.newFile("second.litertlm").apply {
                writeText("second")
            }
            val firstEngine = BlockingEngine(firstModel)
            val secondEngine = RecordingEngine(secondModel)
            var createdEngines = 0

            val runtime = AssistantRuntimeManager(
                modelVersionStore = ModelVersionStore(root),
                evaluationReportStore = EvaluationReportStore(root)
            ) { file ->
                createdEngines += 1
                if (createdEngines == 1) firstEngine else secondEngine
            }

            runtime.load(firstModel)
            val responseJob = launch {
                runtime.respond(
                    history = emptyList(),
                    userMessage = userMessage("hello")
                )
            }
            firstEngine.generationStarted.await()

            val loadJob = launch {
                runtime.load(secondModel)
            }

            kotlinx.coroutines.yield()
            assertTrue(!loadJob.isCompleted)
            assertTrue(!firstEngine.closed)

            firstEngine.releaseGeneration.complete(Unit)
            responseJob.join()
            loadJob.join()

            assertTrue(firstEngine.closed)
            assertTrue(secondEngine.initialized)
            assertTrue(runtime.isReady())
        }
    }

    @Test
    fun failedRuntimeInitializationKeepsPreviousEngineActive() {
        runBlocking {
            val root = temporaryFolder.newFolder("reload-failure")
            val workingModel = temporaryFolder.newFile("working.litertlm").apply {
                writeText("working")
            }
            val failingModel = temporaryFolder.newFile("failing.litertlm").apply {
                writeText("failing")
            }
            val workingEngine = RecordingEngine(workingModel)
            var callCount = 0

            val runtime = AssistantRuntimeManager(
                modelVersionStore = ModelVersionStore(root),
                evaluationReportStore = EvaluationReportStore(root)
            ) {
                callCount += 1
                if (callCount == 1) {
                    workingEngine
                } else {
                    object : AssistantEngine {
                        override suspend fun initialize() {
                            throw IllegalStateException("initialization failed")
                        }

                        override suspend fun generate(
                            history: List<ChatMessage>,
                            userInput: String,
                            loraAdapterFile: File?
                        ): String = error("unreachable")

                        override fun isInitialized(): Boolean = false

                        override fun close() = Unit
                    }
                }
            }

            runtime.load(workingModel)

            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    runtime.load(failingModel)
                }
            }
            assertEquals("initialization failed", error.message)
            assertTrue(runtime.isReady())
        }
    }

    @Test
    fun activeVersionRejectsDifferentBaseModel() {
        runBlocking {
            val root = temporaryFolder.newFolder("base-mismatch")
            val reports = EvaluationReportStore(root)
            val versions = ModelVersionStore(root)
            val baseModel = temporaryFolder.newFile("expected-base.litertlm").apply {
                writeText("expected-base")
            }
            val adapterSource = temporaryFolder.newFile("candidate.source.adapter").apply {
                writeText("adapter")
            }
            val adapter = TrainingArtifactStore(root).publish(adapterSource, "candidate-1")

            versions.registerCandidate(
                ModelVersion(
                    id = "candidate-1",
                    baseModelId = "base-1",
                    adapterFile = adapter,
                    state = ModelVersion.State.CANDIDATE,
                    baseModelSha256 = ArtifactDigest.sha256(baseModel),
                    evaluationReportId = "report-candidate-1",
                    createdAtEpochMs = 1L
                )
            )
            reports.save(
                EvaluationReport(
                    id = "report-candidate-1",
                    baseVersionId = "base-1",
                    candidateVersionId = "candidate-1",
                    candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
                    evaluatedExampleCount = 1,
                    baseMeanError = 1.0,
                    candidateMeanError = 0.9,
                    safetyChecksPassed = true,
                    compatibilityChecksPassed = true,
                    completedAtEpochMs = 2L
                )
            )
            val approvalStore = ActivationApprovalStore(root)
            approvalStore.save(
                ActivationApproval(
                    id = "approval-candidate-1",
                    candidateVersionId = "candidate-1",
                    evaluationReportId = "report-candidate-1",
                    grantedAtEpochMs = 3L
                )
            )
            val activation = ModelActivationCoordinator(
                versions,
                reports,
                approvalStore
            ).activate(
                candidateVersionId = "candidate-1",
                evaluationReportId = "report-candidate-1",
                approvalId = "approval-candidate-1"
            )
            assertTrue(activation is ModelVersionStore.ActivationResult.ACTIVATED)

            val wrongBase = temporaryFolder.newFile("wrong-base.litertlm").apply {
                writeText("wrong-base")
            }
            val runtime = AssistantRuntimeManager(versions, reports) {
                RecordingEngine(it)
            }

            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    runtime.load(wrongBase)
                }
            }
            assertEquals(
                "Loaded base model hash does not match active model version.",
                error.message
            )
        }
    }

    @Test
    fun tamperedActiveAdapterIsRejectedBeforeInference() {
        runBlocking {
        val root = temporaryFolder.newFolder("tamper")
        val reports = EvaluationReportStore(root)
        val versions = ModelVersionStore(root)
        val adapter = publishAndActivate(
            root = root,
            versions = versions,
            reports = reports,
            versionId = "candidate-1",
            baseVersionId = "base-1",
            createdAt = 1L
        )
        val runtime = AssistantRuntimeManager(versions, reports) {
            RecordingEngine(it)
        }
        val model = temporaryFolder.newFile("base.litertlm").apply { writeText("base") }

        runtime.load(model)
        adapter.writeText("tampered")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runtime.respond(emptyList(), userMessage("tamper"))
            }
        }
        }
    }

    private fun publishAndActivate(
        root: File,
        versions: ModelVersionStore,
        reports: EvaluationReportStore,
        versionId: String,
        baseVersionId: String,
        createdAt: Long,
        reportBaseVersionId: String = baseVersionId
    ): File {
        val baseModel = temporaryFolder.newFile(versionId + ".base.litertlm").apply {
            writeText("base")
        }
        val source = temporaryFolder.newFile(versionId + ".source.adapter").apply {
            writeText("adapter-" + versionId)
        }
        val adapter = TrainingArtifactStore(root).publish(source, versionId)
        versions.registerCandidate(
            ModelVersion(
                id = versionId,
                baseModelId = "base-1",
                adapterFile = adapter,
                state = ModelVersion.State.CANDIDATE,
                baseModelSha256 = ArtifactDigest.sha256(baseModel),
                evaluationReportId = "report-" + versionId,
                createdAtEpochMs = createdAt
            )
        )
        reports.save(
            EvaluationReport(
                id = "report-" + versionId,
                baseVersionId = reportBaseVersionId,
                candidateVersionId = versionId,
                candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
                evaluatedExampleCount = 1,
                baseMeanError = 1.0,
                candidateMeanError = 0.9,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = createdAt + 1L
            )
        )

        val approvalStore = ActivationApprovalStore(root)
        approvalStore.save(
            ActivationApproval(
                id = "approval-" + versionId,
                candidateVersionId = versionId,
                evaluationReportId = "report-" + versionId,
                grantedAtEpochMs = createdAt + 2L
            )
        )

        val result = ModelActivationCoordinator(
            versions,
            reports,
            approvalStore
        ).activate(
            candidateVersionId = versionId,
            evaluationReportId = "report-" + versionId,
            approvalId = "approval-" + versionId
        )
        assertTrue(result is ModelVersionStore.ActivationResult.ACTIVATED)
        return adapter
    }

    private fun userMessage(content: String) = ChatMessage(
        id = "user-" + content,
        role = ChatMessage.Role.USER,
        content = content,
        createdAtEpochMs = 1L
    )

    private class BlockingEngine(
        private val modelFile: File
    ) : AssistantEngine {
        val generationStarted = CompletableDeferred<Unit>()
        val releaseGeneration = CompletableDeferred<Unit>()
        var initialized = false
        var closed = false

        override suspend fun initialize() {
            check(modelFile.isFile)
            initialized = true
            closed = false
        }

        override suspend fun generate(
            history: List<ChatMessage>,
            userInput: String,
            loraAdapterFile: File?
        ): String {
            check(initialized)
            generationStarted.complete(Unit)
            releaseGeneration.await()
            return "blocking-response"
        }

        override fun isInitialized(): Boolean = initialized

        override fun close() {
            initialized = false
            closed = true
        }
    }

    private class RecordingEngine(
        private val modelFile: File
    ) : AssistantEngine {
        var initialized = false
        var closed = false
        var lastAdapter: File? = null

        override suspend fun initialize() {
            check(modelFile.isFile)
            initialized = true
            closed = false
        }

        override suspend fun generate(
            history: List<ChatMessage>,
            userInput: String,
            loraAdapterFile: File?
        ): String {
            check(initialized)
            lastAdapter = loraAdapterFile
            return "adapter:" + (loraAdapterFile?.name ?: "none")
        }

        override fun isInitialized(): Boolean = initialized

        override fun close() {
            initialized = false
            closed = true
        }
    }
}
