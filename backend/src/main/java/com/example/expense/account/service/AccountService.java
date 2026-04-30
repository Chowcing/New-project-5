package com.example.expense.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.account.dto.AccountRequest;
import com.example.expense.account.entity.Account;
import com.example.expense.account.mapper.AccountMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public List<Account> list(Long userId) {
        return accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, userId)
                .orderByAsc(Account::getSortOrder)
                .orderByDesc(Account::getId));
    }

    public Account create(Long userId, AccountRequest request) {
        validateNameUnique(userId, request.name(), null);
        Account account = toEntity(new Account(), userId, request);
        accountMapper.insert(account);
        return account;
    }

    public Account update(Long userId, Long id, AccountRequest request) {
        validateNameUnique(userId, request.name(), id);
        Account account = requireOwned(userId, id);
        toEntity(account, userId, request);
        accountMapper.updateById(account);
        return account;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        accountMapper.deleteById(id);
    }

    public Account requireOwned(Long userId, Long id) {
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getId, id)
                .eq(Account::getUserId, userId));
        if (account == null) {
            throw new IllegalArgumentException("账户不存在");
        }
        return account;
    }

    private void validateNameUnique(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<Account> query = new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, userId)
                .eq(Account::getName, name)
                .last("LIMIT 1");
        if (excludeId != null) {
            query.ne(Account::getId, excludeId);
        }
        Account existing = accountMapper.selectOne(query);
        if (existing != null) {
            throw new IllegalArgumentException("账户名称已存在");
        }
    }

    private Account toEntity(Account account, Long userId, AccountRequest request) {
        account.setUserId(userId);
        account.setName(request.name());
        account.setType(request.type());
        account.setBalance(request.balance() == null ? BigDecimal.ZERO : request.balance());
        account.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return account;
    }
}
