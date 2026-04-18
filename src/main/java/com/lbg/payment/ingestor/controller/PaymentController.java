package com.lbg.payment.ingestor.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lbg.payment.ingestor.dto.PaymentRequest;
import com.lbg.payment.ingestor.dto.PaymentResponse;
import com.lbg.payment.ingestor.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Slf4j
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest paymentRequest) throws JsonProcessingException {
        log.info("Received payment request: {}", paymentRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(paymentService.processPayment(paymentRequest));
    }
}
