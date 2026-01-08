package com.aqa.core.services.s3;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;
import java.util.Objects;

import static com.aqa.core.enumerations.Property.AWS_LOGIN_TYPE_DEFAULT;
import static com.aqa.core.enumerations.Property.AWS_PROFILE;
import static software.amazon.awssdk.regions.Region.US_EAST_1;

public class S3ClientProvider {

    private static S3Client client;

    static S3Client get() {
        if (client == null) {
            client = getClient();
        }
        return client;
    }

    private static S3Client getClient() {
        return S3Client.builder()
                .region(US_EAST_1)
                .httpClientBuilder(getHttpClient())
                .credentialsProvider(getProvider())
                .overrideConfiguration(c -> c.retryStrategy(r -> r.maxAttempts(5)))
                .build();
    }

    private static ApacheHttpClient.Builder getHttpClient() {
        return ApacheHttpClient.builder()
                .maxConnections(1000)
                .connectionTimeToLive(Duration.ofMillis(2000))
                .useIdleConnectionReaper(true);
    }

    private static AwsCredentialsProvider getProvider() {
        AWSLoginType type = AWSLoginType.valueOf(System.getProperty("aws.login.type", AWS_LOGIN_TYPE_DEFAULT.read()));
        return switch (type) {
            case SSO -> {
                Objects.requireNonNull(AWS_PROFILE.read(), "AWS profile is null");
                yield ProfileCredentialsProvider.builder().profileName(AWS_PROFILE.read()).build();
            }
            case REMOTE -> {
                InstanceProfileCredentialsProvider instanceProfileProvider = InstanceProfileCredentialsProvider.builder()
                        .asyncCredentialUpdateEnabled(true)
                        .build();
                yield AwsCredentialsProviderChain.builder()
                        .addCredentialsProvider(instanceProfileProvider)
                        .build();
            }
        };
    }

    private enum AWSLoginType {
        SSO,
        REMOTE;
    }
}