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
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final int codeLength;
    private final long expirationMinutes;

    public PasswordResetService(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                MailService mailService,
                                @Value("${app.security.password-reset.code-length:6}") int codeLength,
                                @Value("${app.security.password-reset.expiration-minutes:10}") long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.codeLength = codeLength;
        this.expirationMinutes = expirationMinutes;
    }

    public AppUser findByUsernameOrEmail(String value) {
        String normalized = value == null ? "" : value.trim();
        return userRepository.findByUsername(normalized)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized))
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с таким логином или email не найден"));
    }

    @Transactional
    public void sendResetCode(AppUser user) {
        String code = generateCode();
        user.setPasswordResetCodeHash(passwordEncoder.encode(code));
        user.setPasswordResetCodeExpiresAt(Instant.now().plusSeconds(expirationMinutes * 60));
        userRepository.save(user);
        mailService.sendPasswordResetCode(user.getEmail(), code, expirationMinutes);
    }

    @Transactional
    public boolean verifyCode(AppUser user, String code) {
        if (!StringUtils.hasText(code)
                || !StringUtils.hasText(user.getPasswordResetCodeHash())
                || user.getPasswordResetCodeExpiresAt() == null
                || Instant.now().isAfter(user.getPasswordResetCodeExpiresAt())) {
            clearResetCode(user);
            return false;
        }

        boolean matches = passwordEncoder.matches(code.trim(), user.getPasswordResetCodeHash());
        if (matches) {
            clearResetCode(user);
            return true;
        }
        return false;
    }

    @Transactional
    public void clearResetCode(AppUser user) {
        user.setPasswordResetCodeHash(null);
        user.setPasswordResetCodeExpiresAt(null);
        userRepository.save(user);
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
