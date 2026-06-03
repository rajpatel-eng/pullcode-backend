package com.capstoneproject.codereviewsystem.services.email;

import org.springframework.stereotype.Service;

@Service
public class EmailContentService {

    public String otpSubject() { return "Your OTP - Code Review System"; }
    public String otpBody(String otp) {
        return "Your One-Time Password (OTP) is: " + otp +
               "\n\nValid for 10 minutes. Do not share this OTP with anyone." +
               "\n\n— Code Review System";
    }

    public String welcomeSubject() { return "Welcome to Code Review System!"; }
    public String welcomeBody(String name) {
        return "Hi " + name + ",\n\nWelcome to Code Review System! Your account has been successfully created.\n\n— Code Review System";
    }

    public String forgotPasswordSubject() { return "Reset Your Password - Code Review System"; }
    public String forgotPasswordBody(String otp) {
        return "You requested a password reset.\n\nYour OTP is: " + otp +
               "\n\nValid for 10 minutes.\n\n— Code Review System";
    }

    public String passwordChangedSubject() { return "Password Changed - Code Review System"; }
    public String passwordChangedBody(String name) {
        return "Hi " + name + ",\n\nYour password has been successfully changed." +
               "\n\nIf you did not make this change, contact support immediately.\n\n— Code Review System";
    }

    public String adminOtpSubject() { return "Your Admin Login OTP - Code Review System"; }
    public String adminOtpBody(String name, String otp) {
        return "Hi " + name + ",\n\nYour One-Time Password is: " + otp +
               "\n\nValid for 5 minutes. Do not share this OTP with anyone." +
               "\n\nIf you did not attempt to log in, contact support immediately." +
               "\n\n— Code Review System";
    }

    public String iamWelcomeSubject() { return "Your IAM Account - Code Review System"; }
    public String iamWelcomeBody(String name) {
        return "Hi " + name + ",\n\nAn administrator has created an IAM account for you." +
               "\n\nYou will need to enter a One-Time Password (OTP) sent to this email each time you log in." +
               "\n\nWe recommend changing your password after your first login." +
               "\n\n— Code Review System";
    }

    public String modelMigrationSubject(String repoName) {
        return "[Code Review] AI Model Updated for Repository: " + repoName;
    }
    public String modelMigrationBody(String userName, String repoName, String newModelName) {
        return "Hi " + userName + ",\n\n" +
               "The AI model configured for repository \"" + repoName + "\" has been migrated " +
               "to the default AI model (" + newModelName + ") because the previously selected " +
               "model is no longer available.\n\n" +
               "You can update your repository's AI model at any time from your dashboard." +
               "\n\n— Code Review System";
    }


    public String healthAlertSubject(String modelName, String alertType) {
        return "[ALERT] AI Model Issue Detected: " + modelName + " — " + alertType;
    }

    public String healthAlertBody(String recipientName, String modelName, String alertMessage) {
        return "Hi " + recipientName + ",\n\n" +
               "A health alert has been triggered for AI model: " + modelName + "\n\n" +
               "Details: " + alertMessage + "\n\n" +
               "Please log in to the admin panel to review and take action:\n" +
               "  → Analytics → Model Health → " + modelName + "\n\n" +
               "This alert will auto-resolve once the condition is no longer detected." +
               "\n\n— Code Review System";
    }


    public String costSpikeAlertSubject(String modelName) {
        return "[COST ALERT] Unexpected cost spike detected: " + modelName;
    }

    public String costSpikeAlertBody(String recipientName, String modelName,
                                      double todayCost, double avgCost) {
        return "Hi " + recipientName + ",\n\n" +
               "An unexpected cost spike has been detected for AI model: " + modelName + "\n\n" +
               "Today's cost:     $" + String.format("%.4f", todayCost) + "\n" +
               "7-day average:    $" + String.format("%.4f", avgCost) + "\n\n" +
               "Please review usage and API key configuration in the admin panel." +
               "\n\n— Code Review System";
    }
}