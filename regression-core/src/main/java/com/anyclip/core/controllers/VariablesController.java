package com.anyclip.core.controllers;

import org.apache.commons.beanutils.PropertyUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariablesController {

    private final Map<String, Object> variables = new HashMap<>();

    public Object getVar(String key) {
        if (key.contains(".") || key.contains("]")) {
            List<String> keys = Arrays.asList(key.split("\\."));
            try {
                if (keys.get(0).endsWith("]")) {
                    return PropertyUtils.getProperty(variables.get(key.substring(0, key.indexOf("["))),
                            key.substring(key.indexOf("[")));
                } else {
                    return PropertyUtils.getProperty(variables.get(key.substring(0, key.indexOf("."))),
                            key.substring(key.indexOf(".") + 1));
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new IllegalArgumentException("Variable with key \"%s\" does not exist\n%s".formatted(key, e));
            }
        } else {
            if (variables.containsKey(key)) {
                return variables.get(key);
            } else {
                throw new IllegalArgumentException("Variable with key \"%s\" does not exist".formatted(key));
            }
        }
    }

    public String getVarString(String key) {
        return getVar(key).toString();
    }

    public void setVar(String key, Object value) {
        variables.put(key, value);
    }
}
