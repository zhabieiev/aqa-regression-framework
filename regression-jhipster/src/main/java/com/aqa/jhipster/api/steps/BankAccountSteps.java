package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.models.generated.BankAccount;
import com.aqa.jhipster.api.services.AuthService;
import com.aqa.jhipster.api.services.BankAccountService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BankAccountSteps(BankAccountService bankAccountService, AuthService authService) {

    public BankAccount deleteAndCreate(final BankAccount body) {
        final Map<String, String> headers = authService.getAdminHeaders();
        deleteByName(body.getName(), headers);
        return bankAccountService.create(body, headers);
    }

    public void deleteByName(final String name, final Map<String, String> headers) {
        final List<BankAccount> accounts = bankAccountService.getBankAccounts(headers)
                .stream()
                .filter(account -> Objects.equals(account.getName(), name))
                .toList();
        accounts.forEach(account -> bankAccountService.delete(account.getId(), headers));
    }
}
