package com.cineverse.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    private static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and include a letter and a number";

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @Pattern(regexp = "^$|^[+0-9 -]{7,20}$", message = "Enter a valid phone number") String phone) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token,
                                       @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^$|^[+0-9 -]{7,20}$", message = "Enter a valid phone number") String phone,
            @Size(max = 500) String avatarUrl,
            Long preferredCityId) {}

    public record UserResponse(Long id, String fullName, String email, String phone, String avatarUrl,
                               CatalogDtos.CityResponse preferredCity, List<String> roles, Instant createdAt) {}

    /** refreshToken is also set as an HttpOnly cookie; browsers should rely on the cookie. */
    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {}
}
