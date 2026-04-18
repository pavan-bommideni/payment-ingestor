package com.lbg.payment.ingestor.dto;

public class Violation {
    private String field;
    private String message;

    public Violation(String field, String message) {
        this.field = field;
        this.message = message;
    }

    // Getters
    public String getField() { return field; }
    public String getMessage() { return message; }
}
