package com.aqa.jhipster.ui.steps;

import com.aqa.jhipster.ui.context.UiScenarioContext;
import com.aqa.jhipster.ui.models.LoginBean;
import com.aqa.jhipster.ui.pages.HomePage;
import com.aqa.jhipster.ui.pages.LoginPage;

import java.util.List;

public class LoginSteps {

    private final UiScenarioContext scenarioContext;

    private LoginPage loginPage;
    private HomePage homePage;

    public LoginSteps(final UiScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    public void openLoginPage() {
        loginPage = new LoginPage(scenarioContext.page()).open();
    }

    public void login(final LoginBean credentials, final List<String> headers) {
        homePage = currentLoginPage().login(credentials, headers);
    }

    public void loginExpectingFailure(final LoginBean credentials, final List<String> headers) {
        loginPage = currentLoginPage().loginExpectingFailure(credentials, headers);
    }

    public void assertAuthenticated() {
        currentHomePage().waitUntilAuthenticated();
    }

    public void assertAuthenticationError() {
        currentLoginPage().assertAuthenticationError();
    }

    private LoginPage currentLoginPage() {
        if (loginPage == null) {
            throw new IllegalStateException("The login page has not been opened");
        }

        return loginPage;
    }

    private HomePage currentHomePage() {
        if (homePage == null) {
            throw new IllegalStateException("The user has not completed a successful login");
        }

        return homePage;
    }
}