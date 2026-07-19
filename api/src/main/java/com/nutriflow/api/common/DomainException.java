package com.nutriflow.api.common;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {

    private final String code;
    private final String fieldPath;

    public DomainException(String code, String fieldPath, String message) {
        super(message);
        this.code = code;
        this.fieldPath = fieldPath;
    }
}
