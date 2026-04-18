package com.lbg.payment.ingestor.controller;

import com.lbg.payment.ingestor.entity.Account;
import com.lbg.payment.ingestor.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class AccountController {

    @Autowired
    private AccountService accountService;

    @GetMapping("/api/accounts")
    public ResponseEntity<Account> getAccountById(@RequestParam String accountId) {
        log.info("Received request for account details with accountId: {}", accountId);
        Account account = accountService.getAccountById(accountId);
        return ResponseEntity.ok(account);
    }

}
