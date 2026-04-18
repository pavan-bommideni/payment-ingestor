package com.lbg.payment.ingestor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_incoming")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private String paymentId;

    private String debitAccountId;

    private String creditAccountId;

    private BigDecimal amount;

    private String currency;

    private String reference;

    private Instant timestamp;

    private String status;

    private String message;

}
