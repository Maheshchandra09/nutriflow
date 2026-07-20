package com.nutriflow.api.messaging;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nutriflow.messaging.sqs")
public record SqsMessagingProperties(
        boolean enabled,
        String queueUrl,
        String region,
        URI endpoint,
        long dispatchDelayMs) {}
