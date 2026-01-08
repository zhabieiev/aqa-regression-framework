package com.anyclip.core.steps;

import com.anyclip.core.enumerations.S3FileContentFormat;
import com.anyclip.core.models.S3FileMetaData;
import com.anyclip.core.services.s3.S3ServiceActions;
import com.anyclip.core.utils.FileUtils;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.anyclip.core.utils.FileParseUtils.writeJson;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

public record S3Steps(S3ServiceActions s3ServiceActions) {

    public List<Map<String, String>> getAndFilter(S3FileMetaData fileMetaData) {
        List<Map<String, String>> objectContentList = s3ServiceActions.getObject(fileMetaData);
        return objectContentList.stream().filter(Objects::nonNull).filter(objectContent -> fileMetaData.getFilterValues().stream().anyMatch(val -> {
            String objectContentValue = objectContent.get(fileMetaData.getFilterKey());
            return nonNull(objectContentValue) && objectContentValue.equals(val);
        })).collect(toList());
    }

    public void upload(final S3FileMetaData fileMetaData, List<Map<String, String>> rows) {
        String content = contentAsString(fileMetaData.getContentFormat(), rows);
        File file = FileUtils.createTempFile(fileMetaData.getKey());
        FileUtils.compressToGzip(content, file.toPath());
        s3ServiceActions.upload(fileMetaData, file);
    }

    private String contentAsString(final S3FileContentFormat format, List<Map<String, String>> rows) {
        return switch (format) {
            case JSON_ARRAY -> toJsonArrayFormat(rows);
            case COMMA_SEPARATED_JSONS -> toCommaSeparatedJsons(rows);
        };
    }

    private String toJsonArrayFormat(List<Map<String, String>> rows) {
        return rows.stream().map(row -> {
            try {
                return writeJson().writeValueAsString(row);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.joining(",", "[", "]"));
    }

    private String toCommaSeparatedJsons(List<Map<String, String>> rows) {
        return rows.stream().map(row -> {
            try {
                return writeJson().writeValueAsString(row);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.joining("\n"));
    }
}