package com.lbg.payment.ingestor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbg.payment.ingestor.dao.PaymentRepository;
import com.lbg.payment.ingestor.dto.PaymentRequest;
import com.lbg.payment.ingestor.dto.PaymentResponse;
import com.lbg.payment.ingestor.entity.Account;
import com.lbg.payment.ingestor.entity.Payment;
import com.lbg.payment.ingestor.exception.GlobalExceptionHandler.AccountNotFoundException;
import com.lbg.payment.ingestor.exception.GlobalExceptionHandler.AccountSuspendedException;
import com.lbg.payment.ingestor.exception.GlobalExceptionHandler.DuplicatePaymentException;
import com.lbg.payment.ingestor.kafka.producer.PaymentProducer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class PaymentService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentProducer paymentProducer;

    @Autowired
    private Validator validator;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SUSPENDED = "SUSPENDED";

    public PaymentResponse processPayment(PaymentRequest paymentRequest) throws JsonProcessingException {

        Set<ConstraintViolation<PaymentRequest>> violations = validator.validate(paymentRequest);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        Payment payment = paymentRepository.findById(paymentRequest.getPaymentId()).orElse(null);

        if (payment != null) {
            throw new DuplicatePaymentException("Payment ID already exists: " + paymentRequest.getPaymentId());
        }

        List<Account> accounts = accountService.getAccounts(List.of(paymentRequest.getCreditAccountId(), paymentRequest.getDebitAccountId()));

        Optional<Account> creditAccount = accounts.stream()
            .filter(account -> account.getAccountId().equals(paymentRequest.getCreditAccountId()))
            .findFirst();

        if (creditAccount.isEmpty()) {
            throw new AccountNotFoundException("Credit Account not found: " + paymentRequest.getCreditAccountId());
        }

        if (SUSPENDED.equals(creditAccount.get().getStatus())) {
            throw new AccountSuspendedException("Credit Account is suspended: " + paymentRequest.getCreditAccountId());
        }

        Optional<Account> debitAccount = accounts.stream()
                .filter(account -> account.getAccountId().equals(paymentRequest.getDebitAccountId()))
                .findFirst();

        if (debitAccount.isEmpty()) {
            throw new AccountNotFoundException("Debit Account not found: " + paymentRequest.getCreditAccountId());
        }

        if (SUSPENDED.equals(debitAccount.get().getStatus())) {
            throw new AccountSuspendedException("Debit Account is suspended: " + paymentRequest.getCreditAccountId());
        }

        paymentProducer.sendMessage(paymentRequest.getPaymentId(), objectMapper.writeValueAsString(paymentRequest));

        paymentRepository.save(Payment.builder().paymentId(paymentRequest.getPaymentId())
                .debitAccountId(paymentRequest.getDebitAccountId())
                .creditAccountId(paymentRequest.getCreditAccountId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .reference(paymentRequest.getReference())
                .timestamp(paymentRequest.getTimestamp())
                 .status("SUCCESSFUL")
                 .message("Payment processed successfully")
                .build());

        return new PaymentResponse(paymentRequest.getPaymentId(), "Payment processed successfully");
    }

    // Methods to load payments requests of 10k in cocurrent way
    public List<CompletableFuture<PaymentResponse>> processPaymentsInParallel() {
        List<PaymentRequest> bulkRequest = createBulkPaymentRequests(10000);
        return bulkRequest.stream()
            .map(request -> {
                try {
                    return processPaymentAsync(request);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to process payment request", e);
                }
            }).toList();
    }


    @Async
    private CompletableFuture<PaymentResponse> processPaymentAsync(PaymentRequest paymentRequest) throws JsonProcessingException {
        try {
            PaymentResponse response = processPayment(paymentRequest);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            throw new RuntimeException("Async payment processing failed", e);
        }
    }


    private List<PaymentRequest> createBulkPaymentRequests(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> {
                PaymentRequest request = new PaymentRequest();
                request.setPaymentId(UUID.randomUUID().toString());
                request.setDebitAccountId("30-91-44/12309876");
                request.setCreditAccountId("20-15-88/43917265");
                request.setAmount(new BigDecimal(17059.38));
                request.setCurrency("GBP");
                request.setReference("BULK-PAYMENT-" + (i + 1));
                request.setTimestamp(java.time.Instant.now());
                return request;
            }).collect(Collectors.toList());
    }
}
