package com.lbg.payment.ingestor.dto;

import lombok.Data;

@Data
public class PaymentResponse {
    private String paymentId;
    private String message;

    public PaymentResponse(String paymentId,  String message) {
        this.paymentId = paymentId;
        this.message = message;
    }
}
