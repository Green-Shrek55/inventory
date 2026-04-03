package com.kursach.inventory.web;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.service.PasswordResetService;
import com.kursach.inventory.service.UserService;
import com.kursach.inventory.web.dto.PasswordForm;
import com.kursach.inventory.web.dto.PasswordResetCodeForm;
import com.kursach.inventory.web.dto.PasswordResetRequestForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {
    private static final String RESET_PASSWORD_USER_ID = "RESET_PASSWORD_USER_ID";

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public PasswordResetController(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/password/forgot")
    public String requestResetPage(Model model) {
        if (!model.containsAttribute("requestForm")) {
            model.addAttribute("requestForm", new PasswordResetRequestForm());
        }
        return "password-forgot";
    }

    @PostMapping("/password/forgot")
    public String requestReset(@Valid @ModelAttribute("requestForm") PasswordResetRequestForm requestForm,
                               BindingResult bindingResult,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "password-forgot";
        }

        try {
            AppUser user = passwordResetService.findByUsernameOrEmail(requestForm.getUsernameOrEmail());
            passwordResetService.sendResetCode(user);
            session.setAttribute(RESET_PASSWORD_USER_ID, user.getId());
            redirectAttributes.addFlashAttribute("infoMessage", "Код для смены пароля отправлен на " + maskEmail(user.getEmail()));
            return "redirect:/password/confirm";
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            return "password-forgot";
        }
    }

    @GetMapping("/password/confirm")
    public String confirmResetPage(HttpSession session,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        AppUser user = getResetUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("loginError", "Сначала запросите код для смены пароля");
            return "redirect:/password/forgot";
        }

        if (!model.containsAttribute("codeForm")) {
            model.addAttribute("codeForm", new PasswordResetCodeForm());
        }
        model.addAttribute("maskedEmail", maskEmail(user.getEmail()));
        model.addAttribute("expiresInMinutes", passwordResetService.getExpirationMinutes());
        return "password-confirm";
    }

    @PostMapping("/password/confirm")
    public String confirmReset(@Valid @ModelAttribute("codeForm") PasswordResetCodeForm codeForm,
                               BindingResult bindingResult,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        AppUser user = getResetUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("loginError", "Сначала запросите код для смены пароля");
            return "redirect:/password/forgot";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("maskedEmail", maskEmail(user.getEmail()));
            model.addAttribute("expiresInMinutes", passwordResetService.getExpirationMinutes());
            return "password-confirm";
        }

        if (!passwordResetService.verifyCode(user, codeForm.getCode())) {
            bindingResult.reject("error", "Неверный или просроченный код");
            model.addAttribute("maskedEmail", maskEmail(user.getEmail()));
            model.addAttribute("expiresInMinutes", passwordResetService.getExpirationMinutes());
            return "password-confirm";
        }

        try {
            userService.updatePassword(user.getId(), codeForm.getPassword(), user.getUsername());
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            model.addAttribute("maskedEmail", maskEmail(user.getEmail()));
            model.addAttribute("expiresInMinutes", passwordResetService.getExpirationMinutes());
            return "password-confirm";
        }

        session.removeAttribute(RESET_PASSWORD_USER_ID);
        redirectAttributes.addFlashAttribute("infoMessage", "Пароль успешно изменен. Теперь войдите с новым паролем.");
        return "redirect:/login";
    }

    @GetMapping("/password/reset")
    public String resetPasswordPage(@RequestParam String token,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            userService.getByPasswordResetToken(token);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("loginError", ex.getMessage());
            return "redirect:/login";
        }

        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordForm());
        }
        model.addAttribute("token", token);
        return "password-reset";
    }

    @PostMapping("/password/reset")
    public String resetPassword(@RequestParam String token,
                                @Valid @ModelAttribute("passwordForm") PasswordForm passwordForm,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("token", token);
            return "password-reset";
        }

        try {
            userService.updatePasswordByResetToken(token, passwordForm.getPassword());
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            model.addAttribute("token", token);
            return "password-reset";
        }

        redirectAttributes.addFlashAttribute("infoMessage", "Пароль успешно изменен. Теперь войдите с новым паролем.");
        return "redirect:/login";
    }

    private AppUser getResetUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute(RESET_PASSWORD_USER_ID);
        if (!(userId instanceof Long id)) {
            return null;
        }
        return userService.getById(id);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        if (local.length() <= 2) {
            return "*@" + parts[1];
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + parts[1];
    }
}