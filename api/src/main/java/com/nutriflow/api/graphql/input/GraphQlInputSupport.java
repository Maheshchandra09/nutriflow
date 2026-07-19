package com.nutriflow.api.graphql.input;

import static com.nutriflow.api.common.DomainErrors.validation;

import java.util.UUID;

final class GraphQlInputSupport {

    private GraphQlInputSupport() {}

    static UUID uuid(String value, String fieldPath) {
        if (value == null || value.isBlank()) {
            throw validation(fieldPath, "ID is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw validation(fieldPath, "ID must be a valid UUID");
        }
    }
}
