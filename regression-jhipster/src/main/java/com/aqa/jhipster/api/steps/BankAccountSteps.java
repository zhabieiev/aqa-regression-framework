package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.models.generated.BankAccount;
import com.aqa.jhipster.api.services.AuthService;
import com.aqa.jhipster.api.services.BankAccountService;

import java.util.Map;
import java.util.Objects;

public record BankAccountSteps(BankAccountService bankAccountService, AuthService authService) {

    public BankAccount deleteAndCreate(final BankAccount body) {
        final Map<String, String> headers = authService.getAdminHeaders();

        bankAccountService.getBankAccounts(headers).stream()
                .filter(account -> Objects.equals(account.getName(), body.getName()))
                .map(BankAccount::getId)
                .forEach(id -> bankAccountService.delete(id, headers));

        return bankAccountService.create(body, headers);
    }
}
