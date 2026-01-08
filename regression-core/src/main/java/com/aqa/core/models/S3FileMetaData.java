package com.aqa.core.models;

import com.aqa.core.enumerations.S3FileContentFormat;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
public class S3FileMetaData {

    private String key;
    private String bucketName;
    private String file;
    private Long sizeInBytes;
    private String pathRegex;
    private Integer linesToRead;
    private String lastModified;
    private String filterKey;
    private List<String> filterValues;
    private S3FileContentFormat contentFormat;
}
