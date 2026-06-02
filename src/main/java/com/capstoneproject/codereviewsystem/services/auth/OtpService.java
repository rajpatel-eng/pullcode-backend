package com.capstoneproject.codereviewsystem.services.auth;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.otp.expiry-minutes:10}")
    private long otpExpiryMinutes;

    private static final String OTP_PREFIX        = "OTP:";
    private static final String FORGOT_OTP_PREFIX = "FORGOT_OTP:";
    private static final String ADMIN_OTP_PREFIX  = "ADMIN_OTP:";
    private static final long   ADMIN_OTP_TTL_MIN = 5L;


    public String generateAndStoreOtp(String email) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        log.info("OTP stored for: {} | expires in {} min", email, otpExpiryMinutes);
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        String key    = OTP_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) { log.warn("OTP not found/expired for: {}", email); return false; }
        if (!stored.equals(otp)) { log.warn("Wrong OTP for: {}", email); return false; }
        redisTemplate.delete(key);
        log.info("OTP verified and removed for: {}", email);
        return true;
    }

    public boolean hasActiveOtp(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(OTP_PREFIX + email));
    }

    public long getRemainingTtlSeconds(String email) {
        Long ttl = redisTemplate.getExpire(OTP_PREFIX + email, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }


    public String generateAndStoreForgotOtp(String email) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(FORGOT_OTP_PREFIX + email, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        log.info("Forgot-password OTP stored for: {}", email);
        return otp;
    }

    public boolean verifyForgotOtp(String email, String otp) {
        String key    = FORGOT_OTP_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) { log.warn("Forgot OTP not found/expired: {}", email); return false; }
        if (!stored.equals(otp)) { log.warn("Wrong forgot OTP for: {}", email); return false; }
        redisTemplate.delete(key);
        log.info("Forgot OTP verified and removed for: {}", email);
        return true;
    }

    public boolean hasActiveForgotOtp(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(FORGOT_OTP_PREFIX + email));
    }

    public long getForgotOtpRemainingTtlSeconds(String email) {
        Long ttl = redisTemplate.getExpire(FORGOT_OTP_PREFIX + email, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0;
    }


    public String generateAdminOtp(Long userId) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set(
                ADMIN_OTP_PREFIX + userId, otp, ADMIN_OTP_TTL_MIN, TimeUnit.MINUTES);
        log.info("Admin OTP stored for userId: {} | expires in {} min", userId, ADMIN_OTP_TTL_MIN);
        return otp;
    }

    public boolean verifyAdminOtp(Long userId, String otp) {
        String key    = ADMIN_OTP_PREFIX + userId;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) { log.warn("Admin OTP not found/expired for userId: {}", userId); return false; }
        if (!stored.equals(otp)) { log.warn("Wrong Admin OTP for userId: {}", userId); return false; }
        redisTemplate.delete(key);
        log.info("Admin OTP verified and removed for userId: {}", userId);
        return true;
    }

    public boolean hasActiveAdminOtp(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ADMIN_OTP_PREFIX + userId));
    }


    private String generateOtp() {
        int otp = 100000 + new SecureRandom().nextInt(900000); // 100000–999999
        return String.valueOf(otp);
    }
}