package com.aqa.jhipster.ui.models;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BankAccountBean {

    private String name;
    private BigDecimal balance;
    private String user;
}