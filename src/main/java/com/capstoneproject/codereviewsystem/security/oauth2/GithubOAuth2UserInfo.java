package com.capstoneproject.codereviewsystem.security.oauth2;

import java.util.Map;

public class GithubOAuth2UserInfo extends OAuth2UserInfo {

    public GithubOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getName() {
        String name = (String) attributes.get("name");
        return (name != null && !name.isBlank()) ? name : (String) attributes.get("login");
    }

    @Override
    public String getEmail() {
        String email = (String) attributes.get("email");

        // GitHub returns null even if email is public — use noreply fallback
        if (email == null || email.isBlank()) {
            Object id = attributes.get("id");
            String login = (String) attributes.get("login");
            email = id + "+" + login + "@users.noreply.github.com";
        }

        return email;
    }

    @Override
    public String getImageUrl() {
        return (String) attributes.get("avatar_url");
    }
}