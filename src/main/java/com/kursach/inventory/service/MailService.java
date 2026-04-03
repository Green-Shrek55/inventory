package com.kursach.inventory.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendTwoFactorCode(String email, String code, long expirationMinutes) {
        ensureConfigured();
        log.info("Sending 2FA code to {}", email);
        sendMail(
                email,
                "Your login confirmation code",
                buildTwoFactorBody(code, expirationMinutes),
                "Failed to send 2FA code by email"
        );
    }

    @Async
    public void sendFailedLoginAttemptAlert(String email,
                                            String username,
                                            String ipAddress,
                                            String userAgent,
                                            Instant attemptedAt,
                                            String passwordResetUrl) {
        ensureConfigured();
        log.info("Sending failed login alert to {}", email);
        sendMail(
                email,
                "Предупреждение о неудачной попытке входа",
                buildFailedLoginAttemptBody(username, ipAddress, userAgent, attemptedAt, passwordResetUrl),
                "Failed to send failed login alert"
        );
    }

    @Async
    public void sendPasswordResetCode(String email, String code, long expirationMinutes) {
        ensureConfigured();
        log.info("Sending password reset code to {}", email);
        sendMail(
                email,
                "Код для смены пароля",
                "Вы запросили смену пароля.\n\n"
                        + "Код подтверждения: " + code + "\n"
                        + "Код действует " + expirationMinutes + " минут.",
                "Failed to send password reset code"
        );
    }

    private void ensureConfigured() {
        if (mailSender == null) {
            throw new IllegalStateException("Mail sender is not configured");
        }
    }

    private void sendMail(String email, String subject, String body, String errorMessage) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (StringUtils.hasText(fromAddress)) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (MessagingException | MailAuthenticationException ex) {
            throw new IllegalStateException("Failed to prepare email", ex);
        } catch (MailException ex) {
            throw new IllegalStateException(errorMessage, ex);
        }
    }

    private String buildTwoFactorBody(String code, long expirationMinutes) {
        return "Use this code to complete login: " + code
                + "\n\nThe code is valid for " + expirationMinutes + " minutes.";
    }

    private String buildFailedLoginAttemptBody(String username,
                                               String ipAddress,
                                               String userAgent,
                                               Instant attemptedAt,
                                               String passwordResetUrl) {
        String resolvedIp = StringUtils.hasText(ipAddress) ? ipAddress : "не определен";
        String resolvedUserAgent = StringUtils.hasText(userAgent) ? userAgent : "не определен";
        return "Зафиксирована неудачная попытка входа в вашу учетную запись.\n\n"
                + "Логин: " + username + "\n"
                + "Время: " + DATE_TIME_FORMATTER.format(attemptedAt) + "\n"
                + "IP-адрес: " + resolvedIp + "\n"
                + "User-Agent: " + resolvedUserAgent + "\n\n"
                + "Если это были не вы, смените пароль по ссылке:\n"
                + passwordResetUrl + "\n\n"
                + "Ссылка действует 30 минут.";
    }
}
