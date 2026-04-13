package com.example.demo.service;

import com.example.demo.dto.LoginForm;
import com.example.demo.dto.SignupForm;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class AuthServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthService authService = new AuthService(userAccountRepository, passwordEncoder);

    @Test
    void authenticateShouldRejectDisabledAccount() {
        UserAccountEntity user = buildUser("user1", "user1@example.com", "secret", true);
        user.setEnabled(false);
        when(userAccountRepository.findFirstByAccountIgnoreCaseOrEmailIgnoreCase("user1", "user1")).thenReturn(Optional.of(user));

        AuthService.LoginResult result = authService.authenticate(loginForm("user1", "secret"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isLocked()).isTrue();
        assertThat(result.getMessage()).contains("khóa");
        verify(userAccountRepository, never()).save(any(UserAccountEntity.class));
    }

    @Test
    void authenticateShouldLockAccountAfterRepeatedFailures() {
        UserAccountEntity user = buildUser("user2", "user2@example.com", "secret", true);
        user.setFailedLoginAttempts(4);
        when(userAccountRepository.findFirstByAccountIgnoreCaseOrEmailIgnoreCase("user2", "user2")).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthService.LoginResult result = authService.authenticate(loginForm("user2", "wrong-password"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isLocked()).isTrue();
        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void authenticateShouldResetCountersOnSuccess() {
        UserAccountEntity user = buildUser("user3", "user3@example.com", "secret", true);
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(userAccountRepository.findFirstByAccountIgnoreCaseOrEmailIgnoreCase("user3", "user3")).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthService.LoginResult result = authService.authenticate(loginForm("user3", "secret"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCurrentUser().getAccount()).isEqualTo("user3");
        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository, times(1)).save(captor.capture());
        UserAccountEntity savedUser = captor.getValue();
        assertThat(savedUser.getFailedLoginAttempts()).isZero();
        assertThat(savedUser.getLockedUntil()).isNull();
    }

    @Test
    void registerShouldPrepareAccountWithDefaultSecurityFlags() {
        SignupForm form = new SignupForm();
        form.setAccount("newuser");
        form.setFirstName("New");
        form.setLastName("User");
        form.setEmail("newuser@example.com");
        form.setPassword("secret123");
        form.setConfirmPassword("secret123");
        form.setPhoneNumber("0901234567");
        form.setBirthDate(LocalDate.of(2000, 1, 1));
        form.setAddress("Hanoi");
        form.setAcceptedTerms(true);

        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CurrentUser currentUser = authService.register(form);

        assertThat(currentUser.getAccount()).isEqualTo("newuser");
        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void changePasswordShouldRejectIncorrectCurrentPassword() {
        UserAccountEntity user = buildUser("user4", "user4@example.com", "secret", true);
        user.setId(4L);
        when(userAccountRepository.findById(4L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(4L, "wrong-password", "newSecret123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mật khẩu hiện tại không đúng");

        verify(userAccountRepository, never()).save(any(UserAccountEntity.class));
    }

    @Test
    void changePasswordShouldPersistEncodedPasswordWhenInputIsValid() {
        UserAccountEntity user = buildUser("user5", "user5@example.com", "secret", true);
        user.setId(5L);
        when(userAccountRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.changePassword(5L, "secret", "newSecret123");

        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(captor.capture());
        UserAccountEntity savedUser = captor.getValue();
        assertThat(passwordEncoder.matches("newSecret123", savedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("secret", savedUser.getPasswordHash())).isFalse();
    }

    private UserAccountEntity buildUser(String account, String email, String rawPassword, boolean enabled) {
        UserAccountEntity user = new UserAccountEntity();
        user.setAccount(account);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPhoneNumber("0900000000");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setAddress("Address");
        user.setEnabled(enabled);
        user.setRole("USER");
        user.setRank("BRONZE");
        return user;
    }

    private LoginForm loginForm(String account, String password) {
        LoginForm loginForm = new LoginForm();
        loginForm.setAccount(account);
        loginForm.setPassword(password);
        return loginForm;
    }
}
