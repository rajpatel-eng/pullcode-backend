package com.capstoneproject.codereviewsystem.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class UserRequest {

    @Data
    public static class UpdateName {
        @NotBlank
        @Size(min = 2, message = "Name must be at least 2 characters")
        private String name;
    }

    @Data
    public static class ChangePassword {
        @NotBlank
        private String oldPassword;

        @NotBlank
        @Size(min = 8, message = "New password must be at least 8 characters")
        private String newPassword;
    }

    @Data
    public static class ForgotPasswordSendOtp {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ForgotPasswordReset {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        private String otp;

        @NotBlank
        @Size(min = 8, message = "New password must be at least 8 characters")
        private String newPassword;
    }
}

