package com.kursach.inventory.web;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.repo.UserRepository;
import com.kursach.inventory.service.LoginProtectionService;
import com.kursach.inventory.service.TwoFactorAuthService;
import com.kursach.inventory.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;

@Controller
public class AuthController {
    private static final String TWO_FACTOR_USER_ID = "TWO_FACTOR_USER_ID";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorAuthService twoFactorAuthService;
    private final LoginProtectionService loginProtectionService;
    private final UserService userService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          TwoFactorAuthService twoFactorAuthService,
                          LoginProtectionService loginProtectionService,
                          UserService userService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.twoFactorAuthService = twoFactorAuthService;
        this.loginProtectionService = loginProtectionService;
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(HttpSession session) {
        if (session.getAttribute(TWO_FACTOR_USER_ID) != null) {
            return "redirect:/login/verify";
        }
        return "login";
    }

    @GetMapping("/login/cancel")
    public String cancelLogin(HttpSession session) {
        AppUser user = getPendingUser(session);
        if (user != null) {
            twoFactorAuthService.clearChallenge(user);
            session.removeAttribute(TWO_FACTOR_USER_ID);
        }
        return "redirect:/login";
    }

    @PostMapping("/login")
    public String beginLogin(@RequestParam String username,
                             @RequestParam String password,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findByUsername(username.trim()).orElse(null);
        if (user != null && loginProtectionService.isLocked(user)) {
            redirectAttributes.addFlashAttribute("loginError", loginProtectionService.buildLockedMessage(user));
            redirectAttributes.addFlashAttribute("lockUntilEpochMillis", loginProtectionService.getLockUntilEpochMillis(user));
            return "redirect:/login";
        }

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            if (user != null) {
                loginProtectionService.registerFailedAttempt(user);
                notifyAboutFailedPasswordAttempt(user, request);
                if (loginProtectionService.isLocked(user)) {
                    redirectAttributes.addFlashAttribute("loginError", loginProtectionService.buildLockedMessage(user));
                    redirectAttributes.addFlashAttribute("lockUntilEpochMillis", loginProtectionService.getLockUntilEpochMillis(user));
                    return "redirect:/login";
                }
                redirectAttributes.addFlashAttribute(
                        "loginError",
                        "Неверный логин или пароль. Осталось попыток: " + loginProtectionService.getRemainingAttempts(user)
                );
                return "redirect:/login";
            }
            redirectAttributes.addFlashAttribute("loginError", "Неверный логин или пароль");
            return "redirect:/login";
        }

        loginProtectionService.registerSuccessfulPasswordCheck(user);
        if (!user.isEnabled()) {
            throw new DisabledException("User disabled");
        }

        try {
            twoFactorAuthService.beginChallenge(user);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("loginError", ex.getMessage());
            return "redirect:/login";
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(TWO_FACTOR_USER_ID, user.getId());
        redirectAttributes.addFlashAttribute("infoMessage", "Код подтверждения отправлен на " + maskEmail(user.getEmail()));
        return "redirect:/login/verify";
    }

    @GetMapping("/login/verify")
    public String verifyPage(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        AppUser user = getPendingUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("loginError", "Сначала выполните вход");
            return "redirect:/login";
        }
        model.addAttribute("maskedEmail", maskEmail(user.getEmail()));
        model.addAttribute("expiresInMinutes", twoFactorAuthService.getExpirationMinutes());
        return "login-verify";
    }

    @PostMapping("/login/verify")
    public String verifyCode(@RequestParam String code,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        AppUser user = getPendingUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("loginError", "Сначала выполните вход");
            return "redirect:/login";
        }

        if (!twoFactorAuthService.verifyCode(user, code)) {
            redirectAttributes.addFlashAttribute("verifyError", "Неверный или просроченный код подтверждения");
            return "redirect:/login/verify";
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                );
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.removeAttribute(TWO_FACTOR_USER_ID);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        return "redirect:/post-login";
    }

    @PostMapping("/login/resend")
    public String resendCode(HttpSession session, RedirectAttributes redirectAttributes) {
        AppUser user = getPendingUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("loginError", "Сначала выполните вход");
            return "redirect:/login";
        }

        try {
            twoFactorAuthService.beginChallenge(user);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("verifyError", ex.getMessage());
            return "redirect:/login/verify";
        }

        redirectAttributes.addFlashAttribute("infoMessage", "Новый код отправлен на " + maskEmail(user.getEmail()));
        return "redirect:/login/verify";
    }

    private AppUser getPendingUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute(TWO_FACTOR_USER_ID);
        if (!(userId instanceof Long id)) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }

    private void notifyAboutFailedPasswordAttempt(AppUser user, HttpServletRequest request) {
        try {
            String token = userService.issuePasswordResetToken(user);
            twoFactorAuthService.sendFailedLoginAttemptAlert(
                    user,
                    extractClientIp(request),
                    request.getHeader("User-Agent"),
                    Instant.now(),
                    buildBaseUrl(request) + "/password/reset?token=" + token
            );
        } catch (IllegalStateException ex) {
            // Notification failure must not break authentication flow.
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        if (local.length() <= 2) {
            return "*@" + parts[1];
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + parts[1];
    }

    private String buildBaseUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme()).append("://").append(request.getServerName());
        boolean defaultPort = ("http".equals(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equals(request.getScheme()) && request.getServerPort() == 443);
        if (!defaultPort) {
            url.append(':').append(request.getServerPort());
        }
        url.append(request.getContextPath());
        return url.toString();
    }
}
