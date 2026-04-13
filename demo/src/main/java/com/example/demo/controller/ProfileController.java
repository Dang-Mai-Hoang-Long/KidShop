package com.example.demo.controller;

import com.example.demo.dto.ChangePasswordForm;
import com.example.demo.dto.ProfileUpdateForm;
import com.example.demo.dto.ShippingAddressForm;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.AuthService;
import com.example.demo.service.OrderService;
import com.example.demo.service.ShippingAddressService;
import com.example.demo.service.UserProfileService;
import com.example.demo.validator.ProfileUpdateValidator;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.dao.DataIntegrityViolationException;

@Controller
public class ProfileController {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileService userProfileService;
    private final ShippingAddressService shippingAddressService;
    private final ProfileUpdateValidator profileUpdateValidator;
    private final AuthService authService;
    private final OrderService orderService;

    public ProfileController(UserAccountRepository userAccountRepository, UserProfileService userProfileService, ShippingAddressService shippingAddressService, ProfileUpdateValidator profileUpdateValidator, AuthService authService, OrderService orderService) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileService = userProfileService;
        this.shippingAddressService = shippingAddressService;
        this.profileUpdateValidator = profileUpdateValidator;
        this.authService = authService;
        this.orderService = orderService;
    }

    @InitBinder("profileForm")
    public void initProfileBinder(WebDataBinder binder) {
        binder.addValidators(profileUpdateValidator);
    }

    @GetMapping("/profile")
    public String profile(
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            jakarta.servlet.http.HttpSession session) {
        if (currentUser == null) {
            return "redirect:/login";
        }

        if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return "redirect:/admin";
        }

        Object oauthProfileSuccess = session.getAttribute("profileOAuthSuccess");
        if (oauthProfileSuccess instanceof String message && !message.isBlank() && !model.containsAttribute("profileSuccess")) {
            model.addAttribute("profileSuccess", message);
        }
        session.removeAttribute("profileOAuthSuccess");

        Object oauthProfileError = session.getAttribute("profileOAuthError");
        if (oauthProfileError instanceof String message && !message.isBlank() && !model.containsAttribute("profileError")) {
            model.addAttribute("profileError", message);
        }
        session.removeAttribute("profileOAuthError");

        UserAccountEntity profileUser = userAccountRepository.findById(currentUser.getId()).orElseThrow();

        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", userProfileService.toForm(profileUser));
        }
        ensurePasswordForm(model);
        model.addAttribute("profileUser", profileUser);
        populateProfileOverviewModel(model, currentUser.getId());
        populateShippingAddressModel(model, currentUser.getId(), false);
        return "profile";
    }

    @PostMapping("/profile/gmail/activate")
    public String activateGmail(
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        UserAccountEntity userAccount = userAccountRepository.findById(currentUser.getId()).orElseThrow();
        if (userAccount.isGmailVerified()) {
            redirectAttributes.addFlashAttribute("profileSuccess", "Gmail đã được xác minh trước đó.");
            return "redirect:/profile";
        }

        session.setAttribute("pendingGoogleLinkUserId", currentUser.getId());
        return "redirect:/oauth2/authorization/google";
    }

    @PostMapping(value = "/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profileForm") ProfileUpdateForm profileForm,
            BindingResult bindingResult,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            Model model,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            redirectAttributes.addFlashAttribute("profileError", "Quản trị viên không thể tự chỉnh sửa hồ sơ của chính mình.");
            return "redirect:/admin";
        }

        if (authService.isUserBanned(currentUser)) {
            redirectAttributes.addFlashAttribute("profileError", "Tài khoản của bạn đã bị khóa. Không thể thực hiện thao tác này.");
            return "redirect:/login";
        }

        UserAccountEntity persistedUser = userAccountRepository.findById(currentUser.getId()).orElseThrow();
        profileForm.setId(currentUser.getId());
        profileForm.setAccount(persistedUser.getAccount());
        profileForm.setEmail(persistedUser.getEmail());

        if (bindingResult.hasErrors()) {
            model.addAttribute("activePanel", "profile-panel");
            model.addAttribute("profileUser", persistedUser);
            ensurePasswordForm(model);
            populateProfileOverviewModel(model, currentUser.getId());
            populateShippingAddressModel(model, currentUser.getId(), true);
            model.addAttribute("profileError", "Cập nhật hồ sơ thất bại. Vui lòng kiểm tra lại thông tin đã nhập.");
            return "profile";
        }

        try {
            CurrentUser updated = userProfileService.updateProfile(profileForm);
            session.setAttribute("currentUser", updated);
            redirectAttributes.addFlashAttribute("profileSuccess", "Cập nhật hồ sơ thành công.");
            return "redirect:/profile";
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            model.addAttribute("activePanel", "profile-panel");
            model.addAttribute("profileUser", persistedUser);
            ensurePasswordForm(model);
            populateProfileOverviewModel(model, currentUser.getId());
            populateShippingAddressModel(model, currentUser.getId(), true);
            model.addAttribute("profileError", "Không thể lưu hồ sơ: thông tin có thể đã tồn tại hoặc không hợp lệ.");
            return "profile";
        }
    }

    @PostMapping("/profile/password")
    public String changePassword(
            @Valid @ModelAttribute("passwordForm") ChangePasswordForm passwordForm,
            BindingResult bindingResult,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        if (authService.isUserBanned(currentUser)) {
            redirectAttributes.addFlashAttribute("profileError", "Tài khoản của bạn đã bị khóa. Không thể thực hiện thao tác này.");
            return "redirect:/login";
        }

        if (passwordForm.getNewPassword() != null
                && passwordForm.getConfirmPassword() != null
                && !passwordForm.getNewPassword().equals(passwordForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp.");
        }

        if (passwordForm.getCurrentPassword() != null
                && passwordForm.getNewPassword() != null
                && passwordForm.getCurrentPassword().equals(passwordForm.getNewPassword())) {
            bindingResult.rejectValue("newPassword", "password.duplicate", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        UserAccountEntity persistedUser = userAccountRepository.findById(currentUser.getId()).orElseThrow();

        if (bindingResult.hasErrors()) {
            model.addAttribute("activePanel", "password-panel");
            model.addAttribute("profileUser", persistedUser);
            if (!model.containsAttribute("profileForm")) {
                model.addAttribute("profileForm", userProfileService.toForm(persistedUser));
            }
            populateProfileOverviewModel(model, currentUser.getId());
            populateShippingAddressModel(model, currentUser.getId(), false);
            model.addAttribute("profileError", "Đổi mật khẩu thất bại. Vui lòng kiểm tra lại thông tin.");
            return "profile";
        }

        try {
            authService.changePassword(currentUser.getId(), passwordForm.getCurrentPassword(), passwordForm.getNewPassword());
            redirectAttributes.addFlashAttribute("profileSuccess", "Đổi mật khẩu thành công.");
            return "redirect:/profile#password-panel";
        } catch (IllegalStateException exception) {
            model.addAttribute("activePanel", "password-panel");
            model.addAttribute("profileUser", persistedUser);
            if (!model.containsAttribute("profileForm")) {
                model.addAttribute("profileForm", userProfileService.toForm(persistedUser));
            }
            populateProfileOverviewModel(model, currentUser.getId());
            populateShippingAddressModel(model, currentUser.getId(), false);
            model.addAttribute("profileError", exception.getMessage());
            return "profile";
        }
    }

    @PostMapping("/profile/shipping-addresses/save")
    public String saveShippingAddress(
            @Valid @ModelAttribute("shippingAddressForm") ShippingAddressForm shippingAddressForm,
            BindingResult bindingResult,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("activePanel", "address-panel");
            model.addAttribute("profileForm", userProfileService.toForm(userAccountRepository.findById(currentUser.getId()).orElseThrow()));
            model.addAttribute("profileUser", userAccountRepository.findById(currentUser.getId()).orElseThrow());
            ensurePasswordForm(model);
            populateProfileOverviewModel(model, currentUser.getId());
            populateShippingAddressModel(model, currentUser.getId(), true);
            model.addAttribute("addressFormError", "Vui lòng kiểm tra lại thông tin địa chỉ giao hàng.");
            return "profile";
        }

        shippingAddressService.save(currentUser.getId(), shippingAddressForm);
        redirectAttributes.addFlashAttribute("profileSuccess", "Đã lưu địa chỉ giao hàng.");
        return "redirect:/profile#address-panel";
    }

    @PostMapping("/profile/shipping-addresses/delete")
    public String deleteShippingAddress(
            @RequestParam("addressId") Long addressId,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        shippingAddressService.delete(currentUser.getId(), addressId);
        redirectAttributes.addFlashAttribute("profileSuccess", "Đã xóa địa chỉ giao hàng.");
        return "redirect:/profile#address-panel";
    }

    @PostMapping("/profile/shipping-addresses/default")
    public String setDefaultShippingAddress(
            @RequestParam("addressId") Long addressId,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        shippingAddressService.setDefault(currentUser.getId(), addressId);
        redirectAttributes.addFlashAttribute("profileSuccess", "Đã đặt địa chỉ mặc định.");
        return "redirect:/profile#address-panel";
    }

    @GetMapping("/account")
    public String accountShortcut() {
        return "redirect:/profile";
    }

    private void populateShippingAddressModel(Model model, Long userId, boolean preserveForm) {
        if (!preserveForm || !model.containsAttribute("shippingAddressForm")) {
            model.addAttribute("shippingAddressForm", new ShippingAddressForm());
        }
        model.addAttribute("shippingAddresses", shippingAddressService.listByUser(userId));
        model.addAttribute("defaultShippingAddress", shippingAddressService.getDefaultForUser(userId));
    }

    private void populateProfileOverviewModel(Model model, Long userId) {
        model.addAttribute("recentOrders", orderService.getRecentOrders(userId, 8));
        model.addAttribute("rankProgress", orderService.buildRankProgress(userId));
    }

    private void ensurePasswordForm(Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new ChangePasswordForm());
        }
    }
}