package com.capstoneproject.codereviewsystem.services.email;



import org.springframework.stereotype.Service;

@Service
public class EmailContentService {

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
}