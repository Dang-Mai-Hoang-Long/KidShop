package com.example.demo.validator;

import com.example.demo.dto.SignupForm;
import com.example.demo.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignupFormValidatorTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final SignupFormValidator validator = new SignupFormValidator(userAccountRepository);

    @Test
    void shouldRejectDuplicatePhoneNumber() {
        SignupForm form = new SignupForm();
        form.setAccount("newuser");
        form.setEmail("newuser@example.com");
        form.setPhoneNumber("0901234567");
        form.setPassword("secret123");
        form.setConfirmPassword("secret123");

        when(userAccountRepository.existsByPhoneNumber("0901234567")).thenReturn(true);

        Errors errors = new BeanPropertyBindingResult(form, "signupForm");
        validator.validate(form, errors);

        assertThat(errors.getFieldError("phoneNumber")).isNotNull();
    }
}
