package com.aqa.core.enumerations;

import com.aqa.core.controllers.VariablesController;
import com.aqa.core.convertors.StringConvertor;

public interface PropertyReader {

    String name();

    default String read() {
        return StringConvertor.convertString("${%s}".formatted(name().toLowerCase().replaceAll("_", ".")), new VariablesController());

    }
}
