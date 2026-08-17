package com.aqa.nextjscommerce.allurefixture;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/** Explicitly selected verification fixture; its resource is outside the normal Commerce feature root. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("allure-fixture")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.aqa.nextjscommerce.allurefixture")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,summary,io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
public class AllureAttachmentFixtureSuite {
}
