package com.capstoneproject.codereviewsystem.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.repos.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (!user.isEmailVerified()) {
            throw new BadCredentialsException(
                    "Email not verified. Please verify your email before logging in.");
        }

        return UserPrincipal.create(user);
    }
}
