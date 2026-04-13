package com.example.demo.repository;

import com.example.demo.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long>, JpaSpecificationExecutor<UserAccountEntity> {

    boolean existsByAccountIgnoreCase(String account);

    boolean existsByAccountIgnoreCaseAndIdNot(String account, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    Optional<UserAccountEntity> findFirstByAccountIgnoreCaseOrEmailIgnoreCase(String account, String email);

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);

    Optional<UserAccountEntity> findByGoogleSub(String googleSub);

    boolean existsByGoogleSubAndIdNot(String googleSub, Long id);

    long countByGmailVerifiedTrue();

    List<UserAccountEntity> findAllByRoleIgnoreCase(String role);
}