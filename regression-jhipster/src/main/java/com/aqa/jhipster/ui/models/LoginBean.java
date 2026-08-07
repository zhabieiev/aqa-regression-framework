package com.aqa.jhipster.ui.models;

import lombok.Data;
import lombok.ToString;

@Data
public class LoginBean {

    private String username;

    @ToString.Exclude
    private String password;
}