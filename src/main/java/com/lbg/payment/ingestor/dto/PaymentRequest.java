package com.lbg.payment.ingestor.dto;


import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;


@Data
public class PaymentRequest {

    @NotBlank(message = "Payment ID cannot be null or empty")
    private String paymentId;

    private String debitAccountId;

    @NotBlank(message = "Credit account ID cannot be null or empty")
    private String creditAccountId;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency cannot be null or empty")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    private String currency;

    private String reference;
    private Instant timestamp;

}
