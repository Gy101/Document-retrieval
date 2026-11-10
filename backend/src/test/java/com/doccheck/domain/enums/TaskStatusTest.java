package com.doccheck.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusTest {

    @Test
    void terminalStatusesIncludeDeadLetter() {
        assertTrue(TaskStatus.isTerminal(TaskStatus.SUCCESS.name()));
        assertTrue(TaskStatus.isTerminal(TaskStatus.FAILED.name()));
        assertTrue(TaskStatus.isTerminal(TaskStatus.DEAD.name()));
        assertFalse(TaskStatus.isTerminal(TaskStatus.QUEUED.name()));
        assertFalse(TaskStatus.isTerminal(TaskStatus.RUNNING.name()));
    }
}
