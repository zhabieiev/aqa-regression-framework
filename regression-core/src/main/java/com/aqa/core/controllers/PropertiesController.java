package com.aqa.core.controllers;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.Iterator;
import java.util.Properties;

@Slf4j
public class PropertiesController {
    private static final String PATH_PROPERTIES = "properties";
    private static final String ENV = "env";
    private static PropertiesController instance;
    private final Properties properties = new Properties();

    private PropertiesController() {
        final String env = System.getProperty(ENV);
        if (env != null) {
            loadProperties(PATH_PROPERTIES, "%s.properties".formatted(env));
        } else {
            throw new IllegalArgumentException("The environment is not defined");
        }
    }

    public static String getProperty(final String name) {
        if (instance == null) {
            instance = new PropertiesController();
        }
        final String property = System.getProperty(name, instance.properties.getProperty(name));
        if (property == null) {
            throw new InvalidParameterException("Missing value for name %s!".formatted(name));
        }
        return property;
    }

    private void loadProperties(String path, String name) {
        final String pathToFile = "%s/%s".formatted(path, name);
        final Properties properties = new Properties();
        try {
            Iterator<URL> iterator = PropertiesController.class.getClassLoader().getResources(pathToFile).asIterator();
            while (iterator.hasNext()) {
                InputStream in = iterator.next().openStream();
                properties.load(in);
                properties.forEach((k, v) -> {
                    String key = (String) k;
                    if (key.startsWith("+")) {
                        loadProperties(path, key.substring(1));
                        properties.remove(k);
                    } else {
                        this.properties.putIfAbsent(k, v);
                    }
                });
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException("Properties file '%s' not found".formatted(pathToFile));
        }
    }
}
