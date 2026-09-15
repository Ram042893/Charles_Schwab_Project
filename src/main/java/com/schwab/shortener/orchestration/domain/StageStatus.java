package com.schwab.shortener.orchestration.domain;

public enum StageStatus {
    PENDING,
    READY,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    SKIPPED,
    ROLLED_BACK,
    WAITING_APPROVAL
}
