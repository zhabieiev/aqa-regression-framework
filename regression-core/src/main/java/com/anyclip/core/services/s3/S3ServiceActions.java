package com.anyclip.core.services.s3;

import com.anyclip.core.models.S3FileMetaData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static com.anyclip.core.utils.FileParseUtils.readJson;

public class S3ServiceActions {

    S3Client s3Client;

    public S3ServiceActions() {
        s3Client = S3ClientProvider.get();
    }

    public void delete(final S3FileMetaData metaData) {
        s3Client.deleteObject(d -> d.bucket(metaData.getBucketName()).key(metaData.getKey()));
    }

    public void upload(final S3FileMetaData metaData, final File file) {
        delete(metaData);
        s3Client.putObject(objReq -> objReq.bucket(metaData.getBucketName()).key(metaData.getKey()),
                RequestBody.fromFile(file));
    }

    public List<Map<String, String>> getObject(final S3FileMetaData metaData) {
        List<Map<String, String>> result = new ArrayList<>();

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(metaData.getBucketName())
                .prefix(metaData.getKey())
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        for (S3Object s3Object : listResponse.contents()) {
            String key = s3Object.key();
            try (InputStream is = getContentAsStream(metaData.getBucketName(), key)) {
                MappingIterator<Map<String, String>> mappingData =
                        readJson().readerFor(new TypeReference<Map<String, String>>() {})
                                .readValues(is);
                List<Map<String, String>> data = mappingData.readAll();
                result.addAll(data);
            } catch (Exception e) {
                throw new Error("Unable to parse input stream.");
            }
        }
        return result;
    }

    private InputStream getContentAsStream(String bucket, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        InputStream rawStream = s3Client.getObject(request);
        if (key.endsWith(".gz")) {
            try {
                return new GZIPInputStream(rawStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return rawStream;
        }
    }
}
