package com.example.aicodingassistant.service;

import com.example.aicodingassistant.api.CallbackClient;
import com.example.aicodingassistant.domain.AnalysisResult;
import com.example.aicodingassistant.domain.FixResult;
import com.example.aicodingassistant.domain.JobContext;
import com.example.aicodingassistant.domain.JobSubmissionStatus;
import com.example.aicodingassistant.dto.CallbackResultRequest;
import com.example.aicodingassistant.dto.SubmitJobRequest;
import com.example.aicodingassistant.dto.SubmitJobResponse;
import com.example.aicodingassistant.logging.JobLogService;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@ApplicationScoped
public class JobOrchestratorService {

    @ConfigProperty(name = "app.repos-root")
    String reposRoot;

    @ConfigProperty(name = "app.base-branch")
    String baseBranch;

    @ConfigProperty(name = "app.ai-coding-assistant.model")
    Optional<String> assistantModel;

    @ConfigProperty(name = "app.jobs.max-concurrency")
    int maxConcurrency;

    @Inject
    RepoManager repoManager;

    @Inject
    AiCodingAssistantRunner aiCodingAssistantRunner;

    @Inject
    CallbackClient callbackClient;

    @Inject
    JobLogService jobLogService;

    private final AtomicLong jobCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, String> activeJobsByTicketId = new ConcurrentHashMap<>();
    private ExecutorService executorService;

    @PostConstruct
    void init() {
        executorService = Executors.newFixedThreadPool(Math.max(1, maxConcurrency));
    }

    public SubmitJobResponse submit(SubmitJobRequest request) {
        String existingJobId = activeJobsByTicketId.putIfAbsent(request.ticketId(), "PENDING");
        if (existingJobId != null) {
            return new SubmitJobResponse(
                    "",
                    JobSubmissionStatus.REJECTED,
                    "A coding assistant job is already active for ticket " + request.ticketId() + ".");
        }

        String jobId = String.valueOf(jobCounter.incrementAndGet());
        activeJobsByTicketId.put(request.ticketId(), jobId);
        JobContext context = new JobContext(jobId, request);
        Consumer<String> logger = jobLogService.loggerForJob(jobId);
        logger.accept("Job accepted. ticketId=" + request.ticketId() + ", repoUrl=" + request.repoUrl());
        logger.accept("Log file: " + jobLogService.getLogPath(jobId));
        executorService.submit(() -> process(context));

        return new SubmitJobResponse(
                jobId,
                JobSubmissionStatus.ACCEPTED,
                "Job accepted and queued for asynchronous processing.");
    }

    private void process(JobContext context) {
        AnalysisResult analyzeResult = null;
        String fixSummary = "";
        String model = configuredModelOrNull();
        boolean callbackAttempted = false;
        Consumer<String> logger = jobLogService.loggerForJob(context.jobId());
        logger.accept("Job processing started.");

        try {
            SubmitJobRequest request = context.request();
            String repoUrl = request.repoUrl().trim();
            try (RepoManager.RepoSlot repoSlot = repoManager.acquire(repoUrl, Path.of(reposRoot), baseBranch, logger)) {
                Path repoDir = repoSlot.path();
                logger.accept("Repository ready at: " + repoDir);

                analyzeResult = aiCodingAssistantRunner.runAnalyze(repoDir, request.ticketId(), request.originalRequest(), model, logger);
                double confidence = analyzeResult.confidence();
                if (confidence < 0.0 || confidence > 1.0) {
                    throw new IllegalStateException("AI confidence must be in [0,1], got: " + confidence);
                }
                String likelyCause = analyzeResult.likelyCause();
                if (likelyCause == null || likelyCause.isBlank()) {
                    throw new IllegalStateException("AI likelyCause must be non-empty.");
                }
                logger.accept("Analysis completed. confidence=" + confidence + ", threshold=" + request.confidenceThreshold() + ", likelyCause=" + likelyCause);

                if (confidence <= request.confidenceThreshold()) {
                    logger.accept("Stopping: confidence does not exceed threshold. No callback will be sent.");
                    return;
                }

                String branch = repoManager.createFixBranch(repoDir, request.ticketId(), logger);
                logger.accept("Fix branch created: " + branch);
                FixResult fixResult = aiCodingAssistantRunner.runFix(
                        repoDir,
                        request.ticketId(),
                        request.originalRequest(),
                        analyzeResult,
                        model,
                        logger);
                fixSummary = (fixResult == null || fixResult.fixSummary() == null) ? "" : fixResult.fixSummary();
                logger.accept("Fix phase completed. fixSummary=" + fixSummary);

                if (!repoManager.hasWorkingTreeChanges(repoDir, logger)) {
                    logger.accept("No repository file changes found after fix phase. No callback will be sent.");
                    return;
                }

                String prUrl = repoManager.commitPushAndCreatePr(
                        repoDir,
                        branch,
                        request.ticketId(),
                        request.originalRequest(),
                        analyzeResult.likelyCause(),
                        fixSummary,
                        confidence,
                        logger);
                logger.accept("PR created: " + prUrl);
                callbackAttempted = true;
                callbackClient.postResult(new CallbackResultRequest(
                        request.ticketId(),
                        prUrl
                ), logger);
            }
            logger.accept("Job " + context.jobId() + " is done (SUCCESS).");
        } catch (Exception e) {
            logger.accept("Job failed with exception: " + e.getMessage());
            if (callbackAttempted) {
                logger.accept("Callback was attempted but delivery failed.");
            } else {
                logger.accept("No callback was attempted.");
            }
            logger.accept("Job " + context.jobId() + " is done (FAILED).");
        } finally {
            activeJobsByTicketId.remove(context.request().ticketId(), context.jobId());
        }
    }

    private String configuredModelOrNull() {
        if (assistantModel.isEmpty()) {
            return null;
        }
        String model = assistantModel.get().trim();
        return model.isEmpty() ? null : model;
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdown();
    }
}
