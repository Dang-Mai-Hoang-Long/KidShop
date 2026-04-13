package com.example.demo.validator;

import com.example.demo.dto.SignupForm;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SignupFormValidator implements Validator {

    private final UserAccountRepository userAccountRepository;

    public SignupFormValidator(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return SignupForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        SignupForm form = (SignupForm) target;

        if (form.getAccount() != null && !form.getAccount().isBlank()
                && userAccountRepository.existsByAccountIgnoreCase(form.getAccount().trim())) {
            errors.rejectValue("account", "account.duplicate", "Tài khoản đã tồn tại");
        }

        if (form.getEmail() != null && !form.getEmail().isBlank()
                && userAccountRepository.existsByEmailIgnoreCase(form.getEmail().trim())) {
            errors.rejectValue("email", "email.duplicate", "Email đã được sử dụng");
        }

        if (form.getPhoneNumber() != null && !form.getPhoneNumber().isBlank()
                && userAccountRepository.existsByPhoneNumber(form.getPhoneNumber().trim())) {
            errors.rejectValue("phoneNumber", "phone.duplicate", "Số điện thoại đã được sử dụng");
        }

        if (form.getPassword() != null && form.getConfirmPassword() != null
                && !form.getPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp");
        }
    }
}