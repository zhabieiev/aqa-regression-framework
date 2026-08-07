package com.aqa.jhipster.ui.definitions;

import com.aqa.jhipster.ui.models.LoginBean;
import com.aqa.jhipster.ui.steps.LoginSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.aqa.core.Populator.populateList;

public class LoginDefinitions {

    private final LoginSteps loginSteps;

    public LoginDefinitions(final LoginSteps loginSteps) {
        this.loginSteps = loginSteps;
    }

    @Given("ui user opens the login page")
    public void userOpensLoginPage() {
        loginSteps.openLoginPage();
    }

    @When("ui user signs in with credentials:")
    public void userSignsIn(final DataTable table) {
        final LoginBean credentials = populateCredentials(table);
        final List<String> headers = table.asLists(String.class).getFirst();
        loginSteps.login(credentials, headers);
    }

    @When("ui user signs in expecting failure:")
    public void userSignsInExpectingFailure(final DataTable table) {
        final LoginBean credentials = populateCredentials(table);
        final List<String> headers = table.asLists(String.class).getFirst();
        loginSteps.loginExpectingFailure(credentials, headers);
    }

    @Then("ui user is authenticated")
    public void userIsAuthenticated() {
        loginSteps.assertAuthenticated();
    }

    @Then("ui authentication error is displayed")
    public void authenticationErrorIsDisplayed() {
        loginSteps.assertAuthenticationError();
    }

    private LoginBean populateCredentials(final DataTable table) {
        final List<LoginBean> credentials = populateList(table.asMaps(String.class, String.class), LoginBean.class);
        if (credentials.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one credentials row, but found: " + credentials.size());
        }
        return credentials.getFirst();
    }
}