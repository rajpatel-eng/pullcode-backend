package com.capstoneproject.codereviewsystem.security.oauth2;

import com.capstoneproject.codereviewsystem.dtos.AuthProvider;
import com.capstoneproject.codereviewsystem.dtos.Role;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
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

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String registrationId = request.getClientRegistration()
                .getRegistrationId().toUpperCase();

        // ← Add this line to see what GitHub is returning
        log.info("OAuth2 [{}] attributes: {}", registrationId, oAuth2User.getAttributes());

        OAuth2UserInfo userInfo = switch (registrationId) {
            case "GOOGLE" -> new GoogleOAuth2UserInfo(oAuth2User.getAttributes());
            case "GITHUB" -> new GithubOAuth2UserInfo(oAuth2User.getAttributes());
            default -> throw new OAuth2AuthenticationException("Provider not supported: " + registrationId);
        };

        if (!StringUtils.hasText(userInfo.getEmail())) {
            throw new OAuth2AuthenticationException(
                    "Email not found from " + registrationId + ". Attributes received: " + oAuth2User.getAttributes());
        }

        User user = userRepository.findByEmail(userInfo.getEmail())
                .map(existing -> {
                    existing.setName(userInfo.getName());
                    existing.setAvatarUrl(userInfo.getImageUrl());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .name(userInfo.getName())
                                .email(userInfo.getEmail())
                                .avatarUrl(userInfo.getImageUrl())
                                .authProvider(AuthProvider.valueOf(registrationId))
                                .emailVerified(true)
                                .roles(Set.of(Role.ROLE_USER))
                                .build()));

        log.info("OAuth2 user loaded: {} via {}", user.getEmail(), registrationId);
        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }
}