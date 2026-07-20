package com.nutriflow.api.messaging;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SqsMessagingProperties.class)
@ConditionalOnProperty(
        prefix = "nutriflow.messaging.sqs",
        name = "enabled",
        havingValue = "true")
public class SqsMessagingConfiguration {

    @Bean
    SqsClient sqsClient(SqsMessagingProperties properties) {
        var builder =
                SqsClient.builder()
                        .region(Region.of(properties.region()))
                        .credentialsProvider(
                                DefaultCredentialsProvider.builder().build())
                        .httpClientBuilder(UrlConnectionHttpClient.builder());
        Optional.ofNullable(properties.endpoint()).ifPresent(builder::endpointOverride);
        return builder.build();
    }
}
