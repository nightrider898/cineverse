package com.cineverse.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }
}
