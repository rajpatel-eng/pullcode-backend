package com.capstoneproject.codereviewsystem.dtos;

import com.capstoneproject.codereviewsystem.dtos.enums.UserStatus;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class IamDtos {


    @Data
    public static class CreateRequest {

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100)
        private String name;

        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
    }

    @Data
    public static class UpdateNameRequest {

        @NotBlank
        @Size(min = 2, max = 100)
        private String name;
    }

    @Data
    public static class UpdateEmailRequest {

        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;
    }

    @Data
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String email;
        private UserStatus status;
        private String createdByEmail;
        private LocalDateTime createdAt;
    }
}