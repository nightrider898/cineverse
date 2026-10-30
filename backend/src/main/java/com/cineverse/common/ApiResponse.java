package com.cineverse.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Uniform envelope for every API response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, ApiError error) {

    public record ApiError(String code, String message, Map<String, String> fields) {}

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "Success", null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message, null));
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, String> fields) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message, fields));
    }
}
