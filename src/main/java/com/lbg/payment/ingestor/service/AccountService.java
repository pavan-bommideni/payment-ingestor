package com.lbg.payment.ingestor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbg.payment.ingestor.dao.AccountRepository;
import com.lbg.payment.ingestor.entity.Account;
import com.lbg.payment.ingestor.exception.GlobalExceptionHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private List<Account> loadJsonAndGetAccounts() throws IOException {
        ClassPathResource resource = new ClassPathResource("accounts.json");
        Account[] accounts = objectMapper.readValue(resource.getFile(), Account[].class);
        return Arrays.asList(accounts);
    }

    @PostConstruct
    public void init() throws IOException {
        List<Account> accounts = loadJsonAndGetAccounts();
        accountRepository.saveAll(accounts);
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccountById(String accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new GlobalExceptionHandler.AccountNotFoundException("Account not found with id: " + accountId));
    }

    public List<Account> getAccounts(List<String> accountIds) {
        return accountRepository.findByAccountIdIn(accountIds);
    }


}
