package com.example.aicodingassistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoManagerTest {

    @Mock
    CommandRunner commandRunner;

    private RepoManager repoManager;

    @BeforeEach
    void setUp() {
        repoManager = new RepoManager(commandRunner);
    }

    @Test
    void createFixBranchUsesNextFreeSuffixWhenOriginBranchExists() throws Exception {
        when(commandRunner.run(any(), anyList(), any()))
                .thenReturn(new CommandRunner.CommandResult(
                        0,
                        List.of(
                                "origin/main",
                                "origin/bugfix/ticket-123",
                                "origin/bugfix/ticket-123-2"
                        )));
        when(commandRunner.runOrThrow(any(), anyList(), anyString(), any())).thenReturn(List.of());

        String branch = repoManager.createFixBranch(Path.of("."), 123L, line -> {});

        assertEquals("bugfix/ticket-123-3", branch);
        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandRunner).runOrThrow(any(), commandCaptor.capture(), anyString(), any());
        assertEquals(List.of("git", "checkout", "-B", "bugfix/ticket-123-3"), commandCaptor.getValue());
    }
}
