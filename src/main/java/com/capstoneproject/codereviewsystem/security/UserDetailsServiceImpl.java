package com.capstoneproject.codereviewsystem.security;

import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.dtos.enums.UserStatus;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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

        if (user.getStatus() == UserStatus.PAUSED) {
            throw new LockedException(
                    "Account is paused. Contact your administrator.");
        }

        if (user.getStatus() == UserStatus.DELETED) {
            throw new DisabledException("Account does not exist.");
        }

        return UserPrincipal.create(user);
    }
}