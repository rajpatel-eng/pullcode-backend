package com.capstoneproject.codereviewsystem.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;

public class AdminAuthDtos {

    @Data
    public static class LoginRequest {

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class PreAuthResponse {
        private final String preAuthToken;
        private final String message;
    }

    @Data
    public static class OtpVerifyRequest {

        @NotBlank(message = "Pre-auth token is required")
        private String preAuthToken;

        @NotBlank
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        private String otp;
    }

    @Data
    public static class IamChangePasswordRequest {

        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }
}