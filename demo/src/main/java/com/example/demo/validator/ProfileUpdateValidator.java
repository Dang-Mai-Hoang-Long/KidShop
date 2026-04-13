package com.example.demo.validator;

import com.example.demo.dto.ProfileUpdateForm;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ProfileUpdateValidator implements Validator {

    private final UserAccountRepository userAccountRepository;

    public ProfileUpdateValidator(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return ProfileUpdateForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ProfileUpdateForm form = (ProfileUpdateForm) target;

        Long userId = form.getId();
        if (userId != null) {
            if (form.getAccount() != null && !form.getAccount().isBlank()
                    && userAccountRepository.existsByAccountIgnoreCaseAndIdNot(form.getAccount().trim(), userId)) {
                errors.rejectValue("account", "account.duplicate", "Tài khoản đã tồn tại");
            }

            if (form.getEmail() != null && !form.getEmail().isBlank()
                    && userAccountRepository.existsByEmailIgnoreCaseAndIdNot(form.getEmail().trim(), userId)) {
                errors.rejectValue("email", "email.duplicate", "Email đã được sử dụng");
            }

            if (form.getPhoneNumber() != null && !form.getPhoneNumber().isBlank()
                    && userAccountRepository.existsByPhoneNumberAndIdNot(form.getPhoneNumber().trim(), userId)) {
                errors.rejectValue("phoneNumber", "phone.duplicate", "Số điện thoại đã được sử dụng");
            }
        }
    }
}