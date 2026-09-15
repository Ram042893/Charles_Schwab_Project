package com.schwab.shortener.orchestration.domain;

public enum WorkflowStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    REPLANNED,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    STOPPED
}
