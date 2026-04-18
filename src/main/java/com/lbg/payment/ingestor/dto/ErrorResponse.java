package com.lbg.payment.ingestor.dto;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String path;
    private List<Violation> violations;

    public ErrorResponse(Instant timestamp, int status, String error, String path, List<Violation> violations) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.path = path;
        this.violations = violations;
    }

    // Getters
    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getPath() { return path; }
    public List<Violation> getViolations() { return violations; }
}
