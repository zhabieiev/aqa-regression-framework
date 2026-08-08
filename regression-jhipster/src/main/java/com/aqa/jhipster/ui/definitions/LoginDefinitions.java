package com.aqa.jhipster.ui.definitions;

import com.aqa.jhipster.ui.models.LoginBean;
import com.aqa.jhipster.ui.steps.LoginSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.aqa.core.convertors.DataTableConverter.convertToSingle;
import static com.aqa.core.convertors.DataTableConverter.getHeaders;

public record LoginDefinitions(LoginSteps loginSteps) {

    @Given("ui user opens the login page")
    public void userOpensLoginPage() {
        loginSteps.openLoginPage();
    }

    @When("ui user signs in with credentials:")
    public void userSignsIn(final DataTable table) {
        loginSteps.login(convertToSingle(table, LoginBean.class), getHeaders(table));
    }

    @When("ui user signs in expecting failure:")
    public void userSignsInExpectingFailure(final DataTable table) {
        loginSteps.loginExpectingFailure(convertToSingle(table, LoginBean.class), getHeaders(table));
    }

    @Then("ui user is authenticated")
    public void userIsAuthenticated() {
        loginSteps.assertAuthenticated();
    }

    @Then("ui authentication error is displayed")
    public void authenticationErrorIsDisplayed() {
        loginSteps.assertAuthenticationError();
    }
}