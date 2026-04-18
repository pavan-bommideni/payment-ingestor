package com.lbg.payment.ingestor.dao;

import com.lbg.payment.ingestor.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByAccountIdIn(List<String> accountIds);
}
