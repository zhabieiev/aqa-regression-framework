package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.models.generated.BankAccount;
import com.aqa.jhipster.api.services.AuthService;
import com.aqa.jhipster.api.services.BankAccountService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public record BankAccountSteps(BankAccountService bankAccountService, AuthService authService) {

    public BankAccount deleteAndCreate(final BankAccount body) {
        final Map<String, String> headers = authService.getAdminHeaders();
        deleteByName(body.getName(), headers);
        final BankAccount response = bankAccountService.create(body, headers);
        log.info("Bank account with id {} is created", response.getId());
        return response;
    }

    public BankAccount getBankAccount(final Long id) {
        return bankAccountService.getBankAccount(id, authService.getAdminHeaders());
    }

    public Map<String, Object> getBankAccount(final Long id, final int statusCode) {
        return bankAccountService.getBankAccount(id, authService.getAdminHeaders(), statusCode);
    }

    public void delete(final Long id) {
        delete(id, authService.getAdminHeaders());
    }

    public void deleteByName(final String name) {
        deleteByName(name, authService.getAdminHeaders());
    }

    private void delete(final Long id, final Map<String, String> headers) {
        bankAccountService.delete(id, headers);
        log.info("Bank account with id {} is deleted", id);
    }

    private void deleteByName(final String name, final Map<String, String> headers) {
        final List<BankAccount> accounts = bankAccountService.getBankAccounts(headers)
                .stream()
                .filter(account -> Objects.equals(account.getName(), name))
                .toList();
        accounts.forEach(account -> delete(account.getId(), headers));
    }
}