package com.lbg.payment.ingestor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "accounts")
@Data
public class Account {

    @Id
    private String accountId;

    private String accountName;

    private String accountType;

    private String status;

    private String currency;

    private LocalDate openedDate;

}
