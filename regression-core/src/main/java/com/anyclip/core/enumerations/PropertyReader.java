package com.anyclip.core.enumerations;

import com.anyclip.core.controllers.VariablesController;
import com.anyclip.core.convertors.StringConvertor;

public interface PropertyReader {

    String name();

    default String read() {
        return StringConvertor.convertString("${%s}".formatted(name().toLowerCase().replaceAll("_", ".")), new VariablesController());

    }
}
