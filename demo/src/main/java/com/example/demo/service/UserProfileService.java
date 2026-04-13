package com.example.demo.service;

import com.example.demo.dto.ProfileUpdateForm;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class UserProfileService {

    private final UserAccountRepository userAccountRepository;
    private final CloudflareR2StorageService cloudflareR2StorageService;

    public UserProfileService(UserAccountRepository userAccountRepository,
                              CloudflareR2StorageService cloudflareR2StorageService) {
        this.userAccountRepository = userAccountRepository;
        this.cloudflareR2StorageService = cloudflareR2StorageService;
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
        return form;
    }

    public CurrentUser updateProfile(ProfileUpdateForm form) {
        UserAccountEntity userAccount = userAccountRepository.findById(form.getId()).orElseThrow();

        userAccount.setFirstName(normalize(form.getFirstName()));
        userAccount.setLastName(normalize(form.getLastName()));
        userAccount.setPhoneNumber(normalize(form.getPhoneNumber()));
        userAccount.setGender(normalize(form.getGender()));
        userAccount.setBirthDate(form.getBirthDate());
        userAccount.setAddress(normalize(form.getAddress()));

        MultipartFile avatarFile = form.getAvatarFile();
        if (avatarFile != null && !avatarFile.isEmpty()) {
            userAccount.setAvatarPath(storeAvatar(userAccount.getAccount(), avatarFile));
        }

        UserAccountEntity saved = userAccountRepository.saveAndFlush(userAccount);
        return CurrentUser.from(saved);
    }

    private String storeAvatar(String account, MultipartFile avatarFile) {
        String safeAccount = normalize(account);
        return cloudflareR2StorageService.uploadImage(avatarFile, "users/" + safeAccount, safeAccount);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}