package com.kursach.inventory.service;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class TwoFactorAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final int codeLength;
    private final long expirationMinutes;

    public TwoFactorAuthService(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                MailService mailService,
                                @Value("${app.security.two-factor.code-length:6}") int codeLength,
                                @Value("${app.security.two-factor.expiration-minutes:10}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.codeLength = codeLength;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public void beginChallenge(AppUser user) {
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalStateException("User does not have an email address");
        }

        String code = generateCode();
        user.setTwoFactorCodeHash(passwordEncoder.encode(code));
        user.setTwoFactorCodeExpiresAt(Instant.now().plusSeconds(expirationMinutes * 60));
        userRepository.save(user);
        mailService.sendTwoFactorCode(user.getEmail(), code, expirationMinutes);
    }

    @Transactional
    public boolean verifyCode(AppUser user, String code) {
        if (!StringUtils.hasText(code)
                || !StringUtils.hasText(user.getTwoFactorCodeHash())
                || user.getTwoFactorCodeExpiresAt() == null
                || Instant.now().isAfter(user.getTwoFactorCodeExpiresAt())) {
            clearChallenge(user);
            return false;
        }

        boolean matches = passwordEncoder.matches(code.trim(), user.getTwoFactorCodeHash());
        if (matches) {
            clearChallenge(user);
            return true;
        }

        return false;
    }

    @Transactional
    public void clearChallenge(AppUser user) {
        user.setTwoFactorCodeHash(null);
        user.setTwoFactorCodeExpiresAt(null);
        userRepository.save(user);
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void sendFailedLoginAttemptAlert(AppUser user,
                                            String ipAddress,
                                            String userAgent,
                                            Instant attemptedAt,
                                            String passwordResetUrl) {
        mailService.sendFailedLoginAttemptAlert(
                user.getEmail(),
                user.getUsername(),
                ipAddress,
                userAgent,
                attemptedAt,
                passwordResetUrl
        );
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}