package com.example.demo.service;

import com.example.demo.dto.LoginForm;
import com.example.demo.dto.SignupForm;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CurrentUser register(SignupForm form) {
        UserAccountEntity userAccount = new UserAccountEntity();
        userAccount.setAccount(normalize(form.getAccount()));
        userAccount.setFirstName(normalize(form.getFirstName()));
        userAccount.setLastName(normalize(form.getLastName()));
        userAccount.setEmail(normalize(form.getEmail()));
        userAccount.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        userAccount.setPhoneNumber(normalize(form.getPhoneNumber()));
        userAccount.setBirthDate(form.getBirthDate());
        userAccount.setAddress(normalize(form.getAddress()));
        userAccount.setRole("USER");
        userAccount.setRank(resolveRank(form.getBirthDate()));
        userAccount.setGender("OTHER");
        userAccount.setAvatarPath(null);
        userAccount.setEnabled(true);
        userAccount.setFailedLoginAttempts(0);
        userAccount.setLockedUntil(null);

        try {
            UserAccountEntity saved = userAccountRepository.save(userAccount);
            return CurrentUser.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("Thông tin đăng ký đã tồn tại", exception);
        }
    }

    @Transactional(readOnly = true)
    public LoginResult authenticate(LoginForm loginForm) {
        String identifier = normalize(loginForm.getAccount());
        Optional<LoginResult> userAccount = userAccountRepository
                .findFirstByAccountIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .map(user -> authenticateUser(user, loginForm.getPassword()));

        return userAccount.orElseGet(() -> LoginResult.failure("Tài khoản/email hoặc mật khẩu không đúng"));
    }

    private LoginResult authenticateUser(UserAccountEntity user, String password) {
        LocalDateTime now = LocalDateTime.now();

        if (isAccountBlocked(user, now)) {
            return LoginResult.locked(describeLockMessage(user, now));
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return LoginResult.locked(describeLockMessage(user, now));
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);

            if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedLoginAttempts(0);
                userAccountRepository.save(user);
                return LoginResult.locked(describeLockMessage(user, now));
            }

            userAccountRepository.save(user);
            return LoginResult.failure("Tài khoản/email hoặc mật khẩu không đúng");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        UserAccountEntity savedUser = userAccountRepository.save(user);
        return LoginResult.success(CurrentUser.from(savedUser));
    }

    @Transactional(readOnly = true)
    public boolean isUserBanned(CurrentUser currentUser) {
        if (currentUser == null) {
            return false;
        }

        return userAccountRepository.findById(currentUser.getId())
                .map(user -> isAccountBlocked(user, LocalDateTime.now()))
                .orElse(true);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserAccountEntity userAccount = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản để đổi mật khẩu."));

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalStateException("Vui lòng nhập mật khẩu hiện tại.");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalStateException("Vui lòng nhập mật khẩu mới.");
        }

        if (newPassword.length() < 8) {
            throw new IllegalStateException("Mật khẩu mới phải có ít nhất 8 ký tự.");
        }

        if (!passwordEncoder.matches(currentPassword, userAccount.getPasswordHash())) {
            throw new IllegalStateException("Mật khẩu hiện tại không đúng.");
        }

        if (passwordEncoder.matches(newPassword, userAccount.getPasswordHash())) {
            throw new IllegalStateException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        userAccount.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(userAccount);
    }

    public boolean isAccountBlocked(UserAccountEntity userAccount, LocalDateTime now) {
        if (userAccount == null) {
            return false;
        }

        if (!userAccount.isEnabled()) {
            return true;
        }

        return userAccount.getLockedUntil() != null && userAccount.getLockedUntil().isAfter(now);
    }

    public GoogleAuthResult authenticateWithGoogle(
            String email,
            String googleSub,
            boolean emailVerified,
            String givenName,
            String familyName,
            String fullName,
            String pictureUrl,
            Long pendingLinkUserId) {

        String normalizedEmail = normalize(email);
        String normalizedSub = normalize(googleSub);

        if (normalizedEmail == null || normalizedEmail.isBlank() || normalizedSub == null || normalizedSub.isBlank()) {
            String error = "Không lấy được thông tin Gmail hợp lệ. Vui lòng thử lại.";
            return pendingLinkUserId != null
                    ? GoogleAuthResult.profileFailure(error)
                    : GoogleAuthResult.loginFailure(error);
        }

        if (pendingLinkUserId != null) {
            return linkGoogleForExistingSessionUser(
                    pendingLinkUserId,
                    normalizedEmail,
                    normalizedSub,
                    emailVerified,
                    pictureUrl);
        }

        Optional<UserAccountEntity> byGoogleSub = userAccountRepository.findByGoogleSub(normalizedSub);
        UserAccountEntity userAccount = byGoogleSub.orElseGet(() ->
                userAccountRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null));

        if (userAccount == null) {
            userAccount = createGoogleUser(normalizedEmail, normalizedSub, emailVerified, givenName, familyName, fullName, pictureUrl);
        } else {
            if (isAccountBlocked(userAccount, LocalDateTime.now())) {
                return GoogleAuthResult.loginFailure(describeLockMessage(userAccount, LocalDateTime.now()));
            }
            applyGoogleLink(userAccount, normalizedSub, emailVerified, pictureUrl);
            userAccount = userAccountRepository.saveAndFlush(userAccount);
        }

        if (isAccountBlocked(userAccount, LocalDateTime.now())) {
            return GoogleAuthResult.loginFailure(describeLockMessage(userAccount, LocalDateTime.now()));
        }

        CurrentUser currentUser = CurrentUser.from(userAccount);
        String redirectPath = "ADMIN".equalsIgnoreCase(currentUser.getRole()) ? "/admin" : "/";
        return GoogleAuthResult.success(currentUser, redirectPath, false, null);
    }

    private GoogleAuthResult linkGoogleForExistingSessionUser(
            Long targetUserId,
            String email,
            String googleSub,
            boolean emailVerified,
            String pictureUrl) {

        UserAccountEntity targetUser = userAccountRepository.findById(targetUserId).orElse(null);
        if (targetUser == null) {
            return GoogleAuthResult.profileFailure("Không tìm thấy tài khoản cần kích hoạt Gmail.");
        }

        if (isAccountBlocked(targetUser, LocalDateTime.now())) {
            return GoogleAuthResult.profileFailure(describeLockMessage(targetUser, LocalDateTime.now()));
        }

        if (targetUser.getEmail() == null || !targetUser.getEmail().equalsIgnoreCase(email)) {
            return GoogleAuthResult.profileFailure("Gmail dùng để kích hoạt phải trùng với email đã đăng ký tài khoản.");
        }

        if (userAccountRepository.existsByGoogleSubAndIdNot(googleSub, targetUser.getId())) {
            return GoogleAuthResult.profileFailure("Gmail này đã được liên kết với tài khoản khác.");
        }

        applyGoogleLink(targetUser, googleSub, emailVerified, pictureUrl);
        UserAccountEntity saved = userAccountRepository.saveAndFlush(targetUser);
        return GoogleAuthResult.success(CurrentUser.from(saved), "/profile", true, "Kích hoạt Gmail thành công.");
    }

    private UserAccountEntity createGoogleUser(
            String email,
            String googleSub,
            boolean emailVerified,
            String givenName,
            String familyName,
            String fullName,
            String pictureUrl) {

        UserAccountEntity userAccount = new UserAccountEntity();
        userAccount.setAccount(generateUniqueAccount(email));

        NameParts nameParts = resolveNameParts(givenName, familyName, fullName, email);
        userAccount.setFirstName(nameParts.firstName());
        userAccount.setLastName(nameParts.lastName());

        userAccount.setEmail(email);
        userAccount.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        userAccount.setPhoneNumber(generateUniquePhoneNumber());
        userAccount.setBirthDate(LocalDate.of(2000, 1, 1));
        userAccount.setAddress("Chưa cập nhật");
        userAccount.setRole("USER");
        userAccount.setRank("BRONZE");
        userAccount.setGender("OTHER");
        userAccount.setEnabled(true);
        userAccount.setFailedLoginAttempts(0);
        userAccount.setLockedUntil(null);

        applyGoogleLink(userAccount, googleSub, emailVerified, pictureUrl);
        return userAccountRepository.saveAndFlush(userAccount);
    }

    private void applyGoogleLink(UserAccountEntity userAccount, String googleSub, boolean emailVerified, String pictureUrl) {
        if (googleSub != null && !googleSub.isBlank()) {
            userAccount.setGoogleSub(googleSub);
        }

        userAccount.setGoogleAvatarUrl(normalize(pictureUrl));

        boolean resolvedVerified = userAccount.isGmailVerified()
            || emailVerified
            || (userAccount.getGoogleSub() != null && !userAccount.getGoogleSub().isBlank());
        userAccount.setGmailVerified(resolvedVerified);
        if (resolvedVerified && userAccount.getGmailVerifiedAt() == null) {
            userAccount.setGmailVerifiedAt(LocalDateTime.now());
        }
    }

    private String generateUniqueAccount(String email) {
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase();
        String base = localPart.replaceAll("[^a-z0-9]", "");
        if (base.length() < 4) {
            base = (base + "user").substring(0, 4);
        }
        if (base.length() > 30) {
            base = base.substring(0, 30);
        }

        String candidate = base;
        int sequence = 1;
        while (userAccountRepository.existsByAccountIgnoreCase(candidate)) {
            String suffix = String.valueOf(sequence++);
            int maxBaseLength = Math.max(1, 30 - suffix.length());
            String trimmedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            candidate = trimmedBase + suffix;
        }
        return candidate;
    }

    private String generateUniquePhoneNumber() {
        String candidate;
        do {
            int random = ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
            candidate = "09" + random;
        } while (userAccountRepository.existsByPhoneNumber(candidate));
        return candidate;
    }

    private NameParts resolveNameParts(String givenName, String familyName, String fullName, String email) {
        String resolvedFirstName = normalize(familyName);
        String resolvedLastName = normalize(givenName);

        if ((resolvedFirstName == null || resolvedFirstName.isBlank()) && (resolvedLastName == null || resolvedLastName.isBlank())) {
            String normalizedFullName = normalize(fullName);
            if (normalizedFullName != null && !normalizedFullName.isBlank()) {
                String[] parts = normalizedFullName.split("\\s+");
                if (parts.length == 1) {
                    resolvedFirstName = "Google";
                    resolvedLastName = parts[0];
                } else {
                    resolvedLastName = parts[parts.length - 1];
                    resolvedFirstName = normalizedFullName.substring(0, normalizedFullName.length() - resolvedLastName.length()).trim();
                }
            }
        }

        if (resolvedFirstName == null || resolvedFirstName.isBlank()) {
            resolvedFirstName = "Google";
        }
        if (resolvedLastName == null || resolvedLastName.isBlank()) {
            String localPart = email.substring(0, email.indexOf('@'));
            resolvedLastName = localPart.length() > 30 ? localPart.substring(0, 30) : localPart;
        }

        return new NameParts(resolvedFirstName, resolvedLastName);
    }

    public static class GoogleAuthResult {
        private final boolean success;
        private final CurrentUser currentUser;
        private final String redirectPath;
        private final boolean profileFlow;
        private final String message;

        private GoogleAuthResult(boolean success, CurrentUser currentUser, String redirectPath, boolean profileFlow, String message) {
            this.success = success;
            this.currentUser = currentUser;
            this.redirectPath = redirectPath;
            this.profileFlow = profileFlow;
            this.message = message;
        }

        public static GoogleAuthResult success(CurrentUser currentUser, String redirectPath, boolean profileFlow, String message) {
            return new GoogleAuthResult(true, currentUser, redirectPath, profileFlow, message);
        }

        public static GoogleAuthResult loginFailure(String message) {
            return new GoogleAuthResult(false, null, "/login", false, message);
        }

        public static GoogleAuthResult profileFailure(String message) {
            return new GoogleAuthResult(false, null, "/profile", true, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public CurrentUser getCurrentUser() {
            return currentUser;
        }

        public String getRedirectPath() {
            return redirectPath;
        }

        public boolean isProfileFlow() {
            return profileFlow;
        }

        public String getMessage() {
            return message;
        }
    }

    private record NameParts(String firstName, String lastName) {
    }

    public String describeLockMessage(UserAccountEntity userAccount, LocalDateTime now) {
        if (userAccount == null) {
            return "Tài khoản của bạn đã bị khóa.";
        }

        if (!userAccount.isEnabled() && userAccount.getLockedUntil() == null) {
            return "Tài khoản của bạn đã bị khóa vĩnh viễn. Vui lòng liên hệ quản trị viên.";
        }

        LocalDateTime lockedUntil = userAccount.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            return "Tài khoản của bạn đang bị khóa đến " + lockedUntil.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) + ".";
        }

        if (lockedUntil != null) {
            return "Tài khoản của bạn đã được mở khóa. Vui lòng đăng nhập lại.";
        }

        return "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.";
    }

    public static class LoginResult {
        private final CurrentUser currentUser;
        private final String message;
        private final boolean locked;

        private LoginResult(CurrentUser currentUser, String message, boolean locked) {
            this.currentUser = currentUser;
            this.message = message;
            this.locked = locked;
        }

        public static LoginResult success(CurrentUser currentUser) {
            return new LoginResult(currentUser, null, false);
        }

        public static LoginResult failure(String message) {
            return new LoginResult(null, message, false);
        }

        public static LoginResult locked(String message) {
            return new LoginResult(null, message, true);
        }

        public CurrentUser getCurrentUser() {
            return currentUser;
        }

        public String getMessage() {
            return message;
        }

        public boolean isLocked() {
            return locked;
        }

        public boolean isSuccess() {
            return currentUser != null;
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String resolveRank(java.time.LocalDate birthDate) {
        return "BRONZE";
    }
}