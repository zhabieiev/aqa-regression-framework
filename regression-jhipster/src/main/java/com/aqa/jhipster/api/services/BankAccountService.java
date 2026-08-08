package com.aqa.jhipster.api.services;

import com.aqa.jhipster.api.models.generated.BankAccount;
import jakarta.ws.rs.core.GenericType;

import java.util.List;
import java.util.Map;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.*;
import static java.lang.String.format;
import static java.net.HttpURLConnection.*;

public class BankAccountService extends ApiService {

    private static final String BANK_ACCOUNTS = "/api/bank-accounts";
    private static final String BANK_ACCOUNTS_ID = BANK_ACCOUNTS + "/%s";

    public BankAccount create(final BankAccount body, final Map<String, String> headers) {
        return getResponse(request().method(POST)
                .path(BANK_ACCOUNTS)
                .headers(headers)
                .body(body)
                .statusCode(HTTP_CREATED)
                .build()).readEntity(BankAccount.class);
    }

    public void delete(final Long id, final Map<String, String> headers) {
        getResponse(request().method(DELETE)
                .path(format(BANK_ACCOUNTS_ID, id))
                .headers(headers)
                .statusCode(HTTP_NO_CONTENT)
                .build());
    }

    public List<BankAccount> getBankAccounts(final Map<String, String> headers) {
        return getResponse(request().method(GET)
                .path(BANK_ACCOUNTS)
                .headers(headers)
                .build()).readEntity(new GenericType<>() {});
    }

    public BankAccount getBankAccount(final Long id, final Map<String, String> headers) {
        return getResponse(request().method(GET)
                .path(format(BANK_ACCOUNTS_ID, id))
                .headers(headers)
                .statusCode(HTTP_OK)
                .build()).readEntity(BankAccount.class);
    }

    public Map<String, Object> getBankAccount(final Long id, final Map<String, String> headers, int statusCode) {
        return getResponse(request().method(GET)
                .path(format(BANK_ACCOUNTS_ID, id))
                .headers(headers)
                .statusCode(statusCode)
                .build()).readEntity(new GenericType<>() {});
    }
}