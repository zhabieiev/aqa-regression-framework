package com.anyclip.core.definitions;

import com.anyclip.core.controllers.VariablesController;
import com.anyclip.core.utils.ImageMetadataUtils;
import io.cucumber.java.en.When;

public record ImageMetadataDefinitions(VariablesController variablesController) {

    @When("api user reads image {convertString} dimensions and saves to {string}")
    public void readImage(String image, String var) {
        variablesController.setVar(var, ImageMetadataUtils.getMetadata(image));
    }
}
