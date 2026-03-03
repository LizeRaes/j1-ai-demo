package com.example.appointment.service;

import com.example.appointment.api.CallbackClient;
import com.example.appointment.domain.AnalysisResult;
import com.example.appointment.domain.FixResult;
import com.example.appointment.domain.JobSubmissionStatus;
import com.example.appointment.dto.CallbackResultRequest;
import com.example.appointment.dto.SubmitJobRequest;
import com.example.appointment.dto.SubmitJobResponse;
import com.example.appointment.logging.JobLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class JobOrchestratorServiceTest {

    @Mock
    RepoManager repoManager;

    @Mock
    AiCodingAssistantRunner aiCodingAssistantRunner;

    @Mock
    CallbackClient callbackClient;

    @Mock
    JobLogService jobLogService;

    @Mock
    ExecutorService executorService;

    @Mock
    RepoManager.RepoSlot repoSlot;

    private JobOrchestratorService service;
    private Map<String, List<String>> logsByJob;

    @BeforeEach
    void setUp() throws Exception {
        service = new JobOrchestratorService();
        service.reposRoot = "./repos";
        service.baseBranch = "main";
        service.assistantModel = Optional.empty();
        service.maxConcurrency = 1;
        service.repoManager = repoManager;
        service.aiCodingAssistantRunner = aiCodingAssistantRunner;
        service.callbackClient = callbackClient;
        service.jobLogService = jobLogService;

        logsByJob = new HashMap<>();
        when(jobLogService.loggerForJob(any())).thenAnswer(invocation -> {
            String jobId = invocation.getArgument(0);
            logsByJob.putIfAbsent(jobId, new ArrayList<>());
            return (Consumer<String>) line -> logsByJob.get(jobId).add(line);
        });
        when(jobLogService.getLogPath(any())).thenReturn(java.nio.file.Path.of("/tmp/job.log"));
        when(executorService.submit(any(Runnable.class))).thenReturn(mock(Future.class));
        lenient().when(repoSlot.path()).thenReturn(Path.of("."));
        lenient().when(repoManager.acquire(anyString(), any(), anyString(), any())).thenReturn(repoSlot);

        Field executorField = JobOrchestratorService.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(service, executorService);
    }

    @Test
    void submitRejectsSecondActiveJobForSameTicket() {
        SubmitJobRequest request = new SubmitJobRequest(
                123L,
                "When clicking confirm, user sees error page",
                "https://github.com/LizeRaes/mediflow-user-facing",
                0.6
        );

        SubmitJobResponse first = service.submit(request);
        SubmitJobResponse second = service.submit(request);

        assertEquals(JobSubmissionStatus.ACCEPTED, first.status());
        assertEquals(JobSubmissionStatus.REJECTED, second.status());
        assertTrue(second.message().contains("already active"));

        verify(executorService, times(1)).submit(any(Runnable.class));
        verifyNoInteractions(aiCodingAssistantRunner);
        verifyNoInteractions(callbackClient);
    }

    @Test
    void processStopsWhenConfidenceBelowOrEqualThreshold() throws Exception {
        SubmitJobRequest request = new SubmitJobRequest(123L, "bug report", "https://repo", 0.6);
        when(aiCodingAssistantRunner.runAnalyze(any(), anyLong(), anyString(), any(), any()))
                .thenReturn(new AnalysisResult("likely cause", 0.5));

        service.submit(request);
        Runnable job = captureSubmittedRunnable();
        job.run();

        verify(aiCodingAssistantRunner, never()).runFix(any(), anyLong(), anyString(), any(), any(), any());
        verify(callbackClient, never()).postResult(any(), any());
        assertTrue(logsByJob.get("1").stream().anyMatch(m -> m.contains("Stopping: confidence does not exceed threshold")));
    }

    @Test
    void processFailsWhenLikelyCauseIsBlank() throws Exception {
        SubmitJobRequest request = new SubmitJobRequest(123L, "bug report", "https://repo", 0.6);
        when(aiCodingAssistantRunner.runAnalyze(any(), anyLong(), anyString(), any(), any()))
                .thenReturn(new AnalysisResult(" ", 0.9));

        service.submit(request);
        Runnable job = captureSubmittedRunnable();
        job.run();

        verify(aiCodingAssistantRunner, never()).runFix(any(), anyLong(), anyString(), any(), any(), any());
        verify(callbackClient, never()).postResult(any(), any());
        assertTrue(logsByJob.get("1").stream().anyMatch(m -> m.contains("Job 1 is done (FAILED)")));
    }

    @Test
    void processSkipsCallbackWhenNoWorkingTreeChanges() throws Exception {
        SubmitJobRequest request = new SubmitJobRequest(123L, "bug report", "https://repo", 0.6);
        when(aiCodingAssistantRunner.runAnalyze(any(), anyLong(), anyString(), any(), any()))
                .thenReturn(new AnalysisResult("likely cause", 0.9));
        when(aiCodingAssistantRunner.runFix(any(), anyLong(), anyString(), any(), any(), any()))
                .thenReturn(new FixResult("fixed one thing"));
        when(repoManager.createFixBranch(any(), anyLong(), any())).thenReturn("bugfix/ticket-123");
        when(repoManager.hasWorkingTreeChanges(any(), any())).thenReturn(false);

        service.submit(request);
        Runnable job = captureSubmittedRunnable();
        job.run();

        verify(repoManager, never()).commitPushAndCreatePr(any(), anyString(), anyLong(), anyString(), anyString(), anyString(), anyDouble(), any());
        verify(callbackClient, never()).postResult(any(), any());
        assertTrue(logsByJob.get("1").stream().anyMatch(m -> m.contains("No repository file changes found")));
    }

    @Test
    void processHappyPathSendsMinimalCallbackPayload() throws Exception {
        SubmitJobRequest request = new SubmitJobRequest(123L, "bug report", "https://repo", 0.6);
        when(aiCodingAssistantRunner.runAnalyze(any(), anyLong(), anyString(), any(), any()))
                .thenReturn(new AnalysisResult("likely cause", 0.9));
        when(aiCodingAssistantRunner.runFix(any(), anyLong(), anyString(), any(), any(), any()))
                .thenReturn(new FixResult("fixed one thing"));
        when(repoManager.createFixBranch(any(), anyLong(), any())).thenReturn("bugfix/ticket-123");
        when(repoManager.hasWorkingTreeChanges(any(), any())).thenReturn(true);
        when(repoManager.commitPushAndCreatePr(any(), anyString(), anyLong(), anyString(), anyString(), anyString(), anyDouble(), any()))
                .thenReturn("https://github.com/org/repo/pull/123");

        service.submit(request);
        Runnable job = captureSubmittedRunnable();
        job.run();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<CallbackResultRequest> callbackCaptor =
                org.mockito.ArgumentCaptor.forClass(CallbackResultRequest.class);
        verify(callbackClient).postResult(callbackCaptor.capture(), any());
        assertEquals(123L, callbackCaptor.getValue().ticketId());
        assertEquals("https://github.com/org/repo/pull/123", callbackCaptor.getValue().prUrl());
        assertTrue(logsByJob.get("1").stream().anyMatch(m -> m.contains("Job 1 is done (SUCCESS)")));
    }

    @Test
    void callbackFailureStillReleasesTicketLockForResubmit() throws Exception {
        SubmitJobRequest request = new SubmitJobRequest(123L, "bug report", "https://repo", 0.6);
        when(aiCodingAssistantRunner.runAnalyze(any(), anyLong(), anyString(), any(), any()))
                .thenReturn(new AnalysisResult("likely cause", 0.9));
        when(aiCodingAssistantRunner.runFix(any(), anyLong(), anyString(), any(), any(), any()))
                .thenReturn(new FixResult("fixed one thing"));
        when(repoManager.createFixBranch(any(), anyLong(), any())).thenReturn("bugfix/ticket-123");
        when(repoManager.hasWorkingTreeChanges(any(), any())).thenReturn(true);
        when(repoManager.commitPushAndCreatePr(any(), anyString(), anyLong(), anyString(), anyString(), anyString(), anyDouble(), any()))
                .thenReturn("https://github.com/org/repo/pull/123");
        doThrow(new RuntimeException("connection refused")).when(callbackClient).postResult(any(), any());

        // doThrow means callback will fail
        SubmitJobResponse first = service.submit(request);
        captureSubmittedRunnable().run();
        // after failed first job finishes, submit second job with same ticketId
        SubmitJobResponse second = service.submit(request);

        assertEquals(JobSubmissionStatus.ACCEPTED, first.status());
        assertEquals(JobSubmissionStatus.ACCEPTED, second.status());
        assertTrue(logsByJob.get("1").stream().anyMatch(m -> m.contains("Callback was attempted but delivery failed.")));
    }

    private Runnable captureSubmittedRunnable() {
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Runnable> runnableCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(executorService, atLeastOnce()).submit(runnableCaptor.capture());
        return runnableCaptor.getValue();
    }
}
