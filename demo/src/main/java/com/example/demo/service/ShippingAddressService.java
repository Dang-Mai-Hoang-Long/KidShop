package com.example.demo.service;

import com.example.demo.dto.ShippingAddressForm;
import com.example.demo.entity.ShippingAddressEntity;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.repository.ShippingAddressRepository;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ShippingAddressService {

    private final ShippingAddressRepository shippingAddressRepository;
    private final UserAccountRepository userAccountRepository;

    public ShippingAddressService(ShippingAddressRepository shippingAddressRepository, UserAccountRepository userAccountRepository) {
        this.shippingAddressRepository = shippingAddressRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<ShippingAddressEntity> listByUser(Long userId) {
        return shippingAddressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public ShippingAddressEntity getForUser(Long addressId, Long userId) {
        return shippingAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy địa chỉ giao hàng."));
    }

    @Transactional(readOnly = true)
    public ShippingAddressEntity getDefaultForUser(Long userId) {
        return shippingAddressRepository.findFirstByUserIdAndDefaultAddressTrue(userId)
                .orElseGet(() -> shippingAddressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                        .stream()
                        .findFirst()
                        .orElse(null));
    }

    public ShippingAddressEntity save(Long userId, ShippingAddressForm form) {
        UserAccountEntity user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản người dùng."));

        ShippingAddressEntity address = form.getId() != null
                ? shippingAddressRepository.findByIdAndUserId(form.getId(), userId).orElseGet(ShippingAddressEntity::new)
                : new ShippingAddressEntity();

        address.setUser(user);
        address.setLabel(normalize(form.getLabel()));
        address.setRecipientName(normalize(form.getRecipientName()));
        address.setRecipientPhone(normalize(form.getRecipientPhone()));
        address.setAddressLine(normalize(form.getAddressLine()));
        address.setUpdatedAt(LocalDateTime.now());
        if (address.getCreatedAt() == null) {
            address.setCreatedAt(LocalDateTime.now());
        }

        boolean shouldBecomeDefault = form.isDefaultAddress() || shippingAddressRepository.countByUserId(userId) == 0;
        if (shouldBecomeDefault) {
            clearDefaultFlag(userId);
            address.setDefaultAddress(true);
        } else if (address.getId() == null) {
            address.setDefaultAddress(false);
        }

        ShippingAddressEntity saved = shippingAddressRepository.save(address);
        ensureDefaultAddress(userId, saved.getId());
        return saved;
    }

    public void delete(Long userId, Long addressId) {
        ShippingAddressEntity address = getForUser(addressId, userId);
        boolean wasDefault = address.isDefaultAddress();
        shippingAddressRepository.delete(address);
        if (wasDefault) {
            ensureDefaultAddress(userId, null);
        }
    }

    public void setDefault(Long userId, Long addressId) {
        ShippingAddressEntity address = getForUser(addressId, userId);
        clearDefaultFlag(userId);
        address.setDefaultAddress(true);
        address.setUpdatedAt(LocalDateTime.now());
        shippingAddressRepository.save(address);
    }

    private void clearDefaultFlag(Long userId) {
        shippingAddressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .forEach(address -> {
                    if (address.isDefaultAddress()) {
                        address.setDefaultAddress(false);
                        address.setUpdatedAt(LocalDateTime.now());
                        shippingAddressRepository.save(address);
                    }
                });
    }

    private void ensureDefaultAddress(Long userId, Long preferredAddressId) {
        boolean hasDefault = shippingAddressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .stream()
                .anyMatch(ShippingAddressEntity::isDefaultAddress);
        if (hasDefault) {
            return;
        }

        List<ShippingAddressEntity> addresses = shippingAddressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);
        if (addresses.isEmpty()) {
            return;
        }

        ShippingAddressEntity fallback = null;
        if (preferredAddressId != null) {
            fallback = addresses.stream().filter(address -> preferredAddressId.equals(address.getId())).findFirst().orElse(null);
        }
        if (fallback == null) {
            fallback = addresses.get(0);
        }

        fallback.setDefaultAddress(true);
        fallback.setUpdatedAt(LocalDateTime.now());
        shippingAddressRepository.save(fallback);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}