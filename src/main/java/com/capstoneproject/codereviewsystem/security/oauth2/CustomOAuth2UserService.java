package com.capstoneproject.codereviewsystem.security.oauth2;

import com.capstoneproject.codereviewsystem.dtos.AuthProvider;
import com.capstoneproject.codereviewsystem.dtos.Role;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailContentService emailContentService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String registrationId = request.getClientRegistration()
                .getRegistrationId().toUpperCase();

        OAuth2UserInfo userInfo = switch (registrationId) {
            case "GOOGLE" -> new GoogleOAuth2UserInfo(oAuth2User.getAttributes());
            case "GITHUB" -> new GithubOAuth2UserInfo(oAuth2User.getAttributes());
            default -> throw new OAuth2AuthenticationException(
                    "Provider not supported: " + registrationId);
        };

        if (!StringUtils.hasText(userInfo.getEmail())) {
            throw new OAuth2AuthenticationException(
                    "Email not returned from " + registrationId +
                    ". Please make your email public in your " + registrationId + " account settings.");
        }

        boolean isNewUser = !userRepository.existsByEmail(userInfo.getEmail());

        User user = userRepository.findByEmail(userInfo.getEmail())
                .map(existing -> {
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                                User.builder()
                                        .name(userInfo.getName())
                                        .email(userInfo.getEmail())
                                        .avatarUrl(userInfo.getImageUrl())
                                        .authProvider(AuthProvider.valueOf(registrationId))
                                        .emailVerified(registrationId.equals("GOOGLE")) // ← true for Google, false for GitHub
                                        .roles(Set.of(Role.ROLE_USER))
                                        .build()
                                ));

        if (isNewUser) {
            try {
                emailService.sendEmail(
                        user.getEmail(),
                        emailContentService.welcomeSubject(),
                        emailContentService.welcomeBody(user.getName())
                );
                log.info("Welcome email sent to new OAuth2 user: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send welcome email to: {} | error: {}",
                        user.getEmail(), e.getMessage());
            }
        }

        log.info("OAuth2 user loaded: {} via {} | new: {}", user.getEmail(), registrationId, isNewUser);
        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }
}