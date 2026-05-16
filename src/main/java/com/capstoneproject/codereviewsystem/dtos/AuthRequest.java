package com.capstoneproject.codereviewsystem.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthRequest {

    @Data
    public static class Register {
        @NotBlank
        @Size(min = 2)
        private String name;
        @NotBlank
        @Email
        private String email;
        @NotBlank
        @Size(min = 8)
        private String password;
    }

    @Data
    public static class Login {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class ForgotPassword {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ResetPassword {
        @NotBlank
        private String token;
        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }
}
