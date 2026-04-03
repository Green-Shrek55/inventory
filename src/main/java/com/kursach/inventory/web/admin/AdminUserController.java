package com.kursach.inventory.web.admin;

import com.kursach.inventory.domain.AppUser;
import com.kursach.inventory.domain.Department;
import com.kursach.inventory.domain.Role;
import com.kursach.inventory.service.DepartmentService;
import com.kursach.inventory.service.UserService;
import com.kursach.inventory.web.dto.PasswordForm;
import com.kursach.inventory.web.dto.UserForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final DepartmentService departmentService;

    public AdminUserController(UserService userService, DepartmentService departmentService) {
        this.userService = userService;
        this.departmentService = departmentService;
    }

    @ModelAttribute("allDepartments")
    public List<Department> departments() {
        return departmentService.listAll();
    }

    @ModelAttribute("roles")
    public Role[] roles() {
        return Role.values();
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.listAll());
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String newUser(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserForm());
        }
        model.addAttribute("editMode", false);
        return "admin/users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form,
                         BindingResult bindingResult,
                         Authentication auth,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!StringUtils.hasText(form.getPassword())) {
            bindingResult.rejectValue("password", "password.required", "Specify password");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", false);
            return "admin/users/form";
        }
        try {
            userService.create(form.getUsername(), form.getPassword(), form.getEmail(), form.getRole(),
                    form.getDepartmentId(), form.isEnabled(), actor(auth));
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            model.addAttribute("editMode", false);
            return "admin/users/form";
        }
        redirectAttributes.addFlashAttribute("message", "User created");
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        AppUser user = userService.getById(id);
        if (!model.containsAttribute("form")) {
            UserForm form = new UserForm();
            form.setUsername(user.getUsername());
            form.setEmail(user.getEmail());
            form.setRole(user.getRole());
            form.setDepartmentId(user.getDepartment() != null ? user.getDepartment().getId() : null);
            form.setEnabled(user.isEnabled());
            model.addAttribute("form", form);
        }
        model.addAttribute("user", user);
        model.addAttribute("passwordForm", new PasswordForm());
        model.addAttribute("editMode", true);
        return "admin/users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") UserForm form,
                         BindingResult bindingResult,
                         Authentication auth,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", true);
            model.addAttribute("user", userService.getById(id));
            model.addAttribute("passwordForm", new PasswordForm());
            return "admin/users/form";
        }
        try {
            userService.updateProfile(id, form.getEmail(), form.getRole(), form.getDepartmentId(), form.isEnabled(), actor(auth));
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            model.addAttribute("editMode", true);
            model.addAttribute("user", userService.getById(id));
            model.addAttribute("passwordForm", new PasswordForm());
            return "admin/users/form";
        }
        redirectAttributes.addFlashAttribute("message", "User updated");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/password")
    public String changePassword(@PathVariable Long id,
                                 @Valid @ModelAttribute("passwordForm") PasswordForm form,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editMode", true);
            model.addAttribute("user", userService.getById(id));
            model.addAttribute("form", buildFormFromUser(userService.getById(id)));
            return "admin/users/form";
        }
        try {
            userService.updatePassword(id, form.getPassword(), actor(auth));
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("error", ex.getMessage());
            model.addAttribute("editMode", true);
            model.addAttribute("user", userService.getById(id));
            model.addAttribute("form", buildFormFromUser(userService.getById(id)));
            return "admin/users/form";
        }
        redirectAttributes.addFlashAttribute("message", "Password updated");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         Authentication auth,
                         RedirectAttributes redirectAttributes) {
        AppUser target = userService.getById(id);
        if (auth != null && target.getUsername().equals(auth.getName())) {
            redirectAttributes.addFlashAttribute("error", "You cannot delete your own account");
            return "redirect:/admin/users";
        }
        userService.delete(id, actor(auth));
        redirectAttributes.addFlashAttribute("message", "User deleted");
        return "redirect:/admin/users";
    }

    private String actor(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }

    private UserForm buildFormFromUser(AppUser user) {
        UserForm form = new UserForm();
        form.setUsername(user.getUsername());
        form.setEmail(user.getEmail());
        form.setRole(user.getRole());
        form.setDepartmentId(user.getDepartment() != null ? user.getDepartment().getId() : null);
        form.setEnabled(user.isEnabled());
        return form;
    }
}
