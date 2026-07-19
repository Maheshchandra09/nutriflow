package com.nutriflow.api.common;

public final class DomainErrors {

    private DomainErrors() {}

    public static DomainException validation(String fieldPath, String message) {
        return new DomainException("VALIDATION_ERROR", fieldPath, message);
    }

    public static DomainException notFound(String resource, Object id) {
        return new DomainException(
                "NOT_FOUND", resource + ".id", resource + " not found: " + id);
    }

    public static DomainException conflict(String fieldPath, String message) {
        return new DomainException("CONFLICT", fieldPath, message);
    }

    public static DomainException invalidState(String fieldPath, String message) {
        return new DomainException("INVALID_STATE", fieldPath, message);
    }
}
