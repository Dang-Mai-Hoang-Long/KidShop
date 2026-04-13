package com.example.demo.service;

import com.example.demo.dto.ProfileUpdateForm;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@Transactional
public class AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final OrderRepository orderRepository;

    public AdminUserService(UserAccountRepository userAccountRepository, OrderRepository orderRepository) {
        this.userAccountRepository = userAccountRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserAccountEntity> findUsers(String keyword, String status, String gmailVerified, Pageable pageable) {
        Specification<UserAccountEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("account")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("phoneNumber")), like(normalizedKeyword))
            ));
        }

        if (status != null && !status.isBlank()) {
            if ("active".equalsIgnoreCase(status)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("enabled")));
            } else if ("locked".equalsIgnoreCase(status)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("enabled")));
            }
        }

        if (gmailVerified != null && !gmailVerified.isBlank()) {
            if ("verified".equalsIgnoreCase(gmailVerified)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("gmailVerified")));
            } else if ("unverified".equalsIgnoreCase(gmailVerified)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("gmailVerified")));
            }
        }

        return userAccountRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public ProfileUpdateForm toForm(UserAccountEntity userAccount) {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setId(userAccount.getId());
        form.setAccount(userAccount.getAccount());
        form.setFirstName(userAccount.getFirstName());
        form.setLastName(userAccount.getLastName());
        form.setEmail(userAccount.getEmail());
        form.setPhoneNumber(userAccount.getPhoneNumber());
        form.setGender(userAccount.getGender());
        form.setBirthDate(userAccount.getBirthDate());
        form.setAddress(userAccount.getAddress());
        form.setEnabled(userAccount.isEnabled());
        form.setLockDuration(resolveLockDuration(userAccount));
        form.setLockReason(userAccount.getLockReason());
        return form;
    }

    public CurrentUser updateUser(ProfileUpdateForm form, Long actorUserId) {
        UserAccountEntity userAccount = findUser(form.getId());
        ensureCanEditUser(actorUserId, userAccount);

        userAccount.setAccount(normalize(form.getAccount()));
        userAccount.setFirstName(normalize(form.getFirstName()));
        userAccount.setLastName(normalize(form.getLastName()));
        userAccount.setEmail(normalize(form.getEmail()));
        userAccount.setPhoneNumber(normalize(form.getPhoneNumber()));
        userAccount.setGender(normalize(form.getGender()));
        userAccount.setBirthDate(form.getBirthDate());
        userAccount.setAddress(normalize(form.getAddress()));

        applyLockState(userAccount, form.getEnabled(), form.getLockDuration(), form.getLockReason());

        return CurrentUser.from(userAccountRepository.save(userAccount));
    }

    public CurrentUser toggleEnabled(Long userId, Long actorUserId, boolean enabled) {
        return toggleEnabled(userId, actorUserId, enabled, null, null);
    }

    public CurrentUser toggleEnabled(Long userId, Long actorUserId, boolean enabled, String lockDuration, String lockReason) {
        UserAccountEntity userAccount = findUser(userId);
        ensureCanEditUser(actorUserId, userAccount);
        applyLockState(userAccount, enabled, lockDuration, lockReason);
        return CurrentUser.from(userAccountRepository.save(userAccount));
    }

    public CurrentUser setTotalPurchasedAmount(Long userId, Long actorUserId, BigDecimal targetTotalSpend) {
        if (targetTotalSpend == null || targetTotalSpend.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Tổng tiền đã mua phải lớn hơn hoặc bằng 0.");
        }

        UserAccountEntity userAccount = findUser(userId);
        ensureCanEditUser(actorUserId, userAccount);

        BigDecimal normalizedTarget = targetTotalSpend.setScale(0, RoundingMode.HALF_UP);
        BigDecimal deliveredSpend = orderRepository.sumDeliveredRevenueByUser(userId);
        BigDecimal normalizedDeliveredSpend = (deliveredSpend == null ? BigDecimal.ZERO : deliveredSpend)
                .setScale(0, RoundingMode.HALF_UP);

        userAccount.setPurchaseSpendOffset(normalizedTarget.subtract(normalizedDeliveredSpend));
        return CurrentUser.from(userAccountRepository.save(userAccount));
    }

    @Transactional(readOnly = true)
    public UserAccountEntity findUser(Long userId) {
        return userAccountRepository.findById(userId).orElseThrow();
    }

    private void ensureCanEditUser(Long actorUserId, UserAccountEntity targetUser) {
        if (actorUserId == null) return;
        
        if (actorUserId.equals(targetUser.getId())) {
            throw new IllegalStateException("Bạn không thể tự chỉnh sửa thông tin hoặc khóa tài khoản của chính mình trong trang quản trị.");
        }
        
        if ("ADMIN".equalsIgnoreCase(targetUser.getRole())) {
            UserAccountEntity actor = findUser(actorUserId);
            int actorRank = getRankValue(actor.getRank());
            int targetRank = getRankValue(targetUser.getRank());
            
            if (actorRank <= targetRank) {
                throw new IllegalStateException("Không có quyền chỉnh sửa tài khoản Admin có cấp bậc tương đương hoặc cao hơn.");
            }
        }
    }

    private int getRankValue(String rank) {
        if (rank == null) return 1;
        return switch (rank.toUpperCase()) {
            case "DIAMOND" -> 4;
            case "GOLD" -> 3;
            case "SILVER" -> 2;
            case "BRONZE" -> 1;
            default -> 1;
        };
    }

    @Transactional(readOnly = true)
    public long countUsers() {
        return userAccountRepository.count();
    }

    @Transactional(readOnly = true)
    public long countGmailVerifiedUsers() {
        return userAccountRepository.countByGmailVerifiedTrue();
    }

    private String like(String value) {
        return "%" + value + "%";
    }

    private void applyLockState(UserAccountEntity userAccount, Boolean enabled, String lockDuration, String lockReason) {
        userAccount.setFailedLoginAttempts(0);

        boolean isEnabled = Boolean.TRUE.equals(enabled);
        userAccount.setEnabled(isEnabled);

        if (isEnabled) {
            userAccount.setLockedUntil(null);
            userAccount.setLockReason(null);
            return;
        }

        userAccount.setLockReason(lockReason);

        if (lockDuration == null || lockDuration.isBlank() || "PERMANENT".equalsIgnoreCase(lockDuration)) {
            userAccount.setLockedUntil(null);
            return;
        }

        int days = parseLockDays(lockDuration);
        userAccount.setEnabled(true);
        userAccount.setLockedUntil(LocalDateTime.now().plusDays(days));
    }

    private int parseLockDays(String lockDuration) {
        try {
            int days = Integer.parseInt(lockDuration.trim());
            if (days == 1 || days == 3 || days == 7 || days == 14 || days == 30) {
                return days;
            }
        } catch (NumberFormatException ignored) {
        }
        return 7;
    }

    private String resolveLockDuration(UserAccountEntity userAccount) {
        if (!userAccount.isEnabled() && userAccount.getLockedUntil() == null) {
            return "PERMANENT";
        }

        if (userAccount.getLockedUntil() == null) {
            return "";
        }

        long days = ChronoUnit.DAYS.between(LocalDateTime.now().toLocalDate(), userAccount.getLockedUntil().toLocalDate());
        if (days == 1 || days == 3 || days == 7 || days == 14 || days == 30) {
            return String.valueOf(days);
        }

        return "PERMANENT";
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
