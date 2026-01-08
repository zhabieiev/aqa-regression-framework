package com.anyclip.core.definitions;

import com.anyclip.core.controllers.VariablesController;
import com.anyclip.core.models.S3FileMetaData;
import com.anyclip.core.steps.S3Steps;
import io.cucumber.java.en.When;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.anyclip.core.Populator.populate;
import static com.anyclip.core.convertors.MapConvertor.convertMapKeysWithPrefix;
import static com.anyclip.core.enumerations.S3RequestPrefixes.DATA;
import static com.anyclip.core.enumerations.S3RequestPrefixes.META;
import static java.util.Set.of;
import static java.util.stream.Collectors.toList;

public record S3Definitions(S3Steps s3Steps, VariablesController variablesController) {

    @When("s3 user gets files content and saves to {string}:")
    public void get(String var, Map<String, String> map) {
        variablesController.setVar(var, s3Steps.s3ServiceActions().getObject(populate(map, S3FileMetaData.class)));
    }

    @When("s3 user gets and filters files content and saves to {string}:")
    public void getAndFilter(String var, Map<String, String> map) {
        variablesController.setVar(var, s3Steps.getAndFilter(populate(map, S3FileMetaData.class)));
    }

    @When("s3 user uploads files with content:")
    public void upload(List<Map<String, String>> maps) {
       maps.stream().map(map -> {
                    Map<S3FileMetaData, Map<String, String>> tmp = new HashMap<>();
                    S3FileMetaData fileMetadata = populate(convertMapKeysWithPrefix(map, of(META.getValue())), S3FileMetaData.class);
                    Map<String, String> fileContent = convertMapKeysWithPrefix(map, of(DATA.getValue()));
                    tmp.put(fileMetadata, fileContent);
                    return tmp;
                }).flatMap(map -> map.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, toList())))
                .entrySet().stream()
                .map(map -> Map.of(map.getKey(), map.getValue()))
                .forEach(map -> map.forEach(s3Steps::upload));
    }
}