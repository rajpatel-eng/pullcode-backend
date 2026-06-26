package com.capstoneproject.codereviewsystem.configs;

import com.capstoneproject.codereviewsystem.dtos.enums.AuthProvider;
import com.capstoneproject.codereviewsystem.dtos.enums.Role;
import com.capstoneproject.codereviewsystem.dtos.enums.UserStatus;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.name}")
    private String adminName;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin already exists: {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .name(adminName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .roles(Set.of(Role.ROLE_ADMIN))
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);
        log.info("Default admin created: {}", adminEmail);
    }
}