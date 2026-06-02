package com.capstoneproject.codereviewsystem.services.email;

import org.springframework.stereotype.Service;

// MODIFIED: Added admin OTP, IAM welcome, and model migration email templates
@Service
public class EmailContentService {

    // ── User registration OTP ─────────────────────────────────────────────────

    public String otpSubject() {
        return "Your OTP - Code Review System";
    }

    public String otpBody(String otp) {
        return """
                Your One-Time Password (OTP) is: %s

                Valid for 10 minutes.
                Do not share this OTP with anyone.

                If you did not request this, please ignore this email.

                — Code Review System
                """.formatted(otp);
    }

    // ── Welcome (ROLE_USER) ───────────────────────────────────────────────────

    public String welcomeSubject() {
        return "Welcome to Code Review System!";
    }

    public String welcomeBody(String name) {
        return """
                Hi %s,

                Welcome to Code Review System!
                Your account has been successfully created.

                — Code Review System
                """.formatted(name);
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    public String forgotPasswordSubject() {
        return "Reset Your Password - Code Review System";
    }

    public String forgotPasswordBody(String otp) {
        return """
                You requested a password reset for your Code Review System account.

                Your OTP is: %s

                Valid for 10 minutes.
                Do not share this OTP with anyone.

                If you did not request this, please ignore this email.

                — Code Review System
                """.formatted(otp);
    }

    public String passwordChangedSubject() {
        return "Password Changed - Code Review System";
    }

    public String passwordChangedBody(String name) {
        return """
                Hi %s,

                Your password has been successfully changed.

                If you did not make this change, please contact support immediately.

                — Code Review System
                """.formatted(name);
    }

    // ── Admin / IAM 2FA OTP ───────────────────────────────────────────────────

    public String adminOtpSubject() {
        return "Your Admin Login OTP - Code Review System";
    }

    public String adminOtpBody(String name, String otp) {
        return """
                Hi %s,

                A login attempt was made for your administrator account.

                Your One-Time Password is: %s

                Valid for 5 minutes.
                Do not share this OTP with anyone.

                If you did not attempt to log in, please contact support immediately.

                — Code Review System
                """.formatted(name, otp);
    }

    // ── IAM account created by Admin ─────────────────────────────────────────

    public String iamWelcomeSubject() {
        return "Your IAM Account - Code Review System";
    }

    public String iamWelcomeBody(String name) {
        return """
                Hi %s,

                An administrator has created an IAM account for you on Code Review System.

                You can log in at the admin portal using your email and the password
                provided by your administrator.

                For security, you will be required to enter a One-Time Password (OTP)
                sent to this email address each time you log in.

                We recommend changing your password after your first login.

                — Code Review System
                """.formatted(name);
    }

    // ── AI Model migration notification ──────────────────────────────────────

    public String modelMigrationSubject(String repoName) {
        return "[Code Review] AI Model Updated for Repository: " + repoName;
    }

    public String modelMigrationBody(String userName, String repoName, String newModelName) {
        return """
                Hi %s,

                The AI model configured for repository "%s" has been migrated to the
                default AI model (%s) because the previously selected model is no longer
                available.

                You can update your repository's AI model at any time from your dashboard.

                If you have any questions, please contact your administrator.

                — Code Review System
                """.formatted(userName, repoName, newModelName);
    }
}