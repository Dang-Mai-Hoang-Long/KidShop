package com.example.demo.model;

import com.example.demo.entity.UserAccountEntity;

import java.io.Serializable;

public class CurrentUser implements Serializable {

    private final Long id;
    private final String account;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String role;
    private final String rank;
    private final String gender;
    private final String avatarPath;

    public CurrentUser(Long id, String account, String firstName, String lastName, String email, String role, String rank, String gender, String avatarPath) {
        this.id = id;
        this.account = account;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.rank = rank;
        this.gender = gender;
        this.avatarPath = avatarPath;
    }

    public static CurrentUser from(UserAccountEntity userAccount) {
        return new CurrentUser(
                userAccount.getId(),
                userAccount.getAccount(),
                userAccount.getFirstName(),
                userAccount.getLastName(),
                userAccount.getEmail(),
                userAccount.getRole(),
                userAccount.getRank(),
                userAccount.getGender(),
                userAccount.getAvatarPath());
    }

    public Long getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getRank() {
        return rank;
    }

    public String getGender() {
        return gender;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public String getDisplayName() {
        StringBuilder builder = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            builder.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(lastName.trim());
        }
        if (builder.isEmpty()) {
            return account;
        }
        return builder.toString();
    }

    public String getRankLabel() {
        if (rank == null) {
            return "Đồng";
        }

        return switch (rank.toUpperCase()) {
            case "DIAMOND" -> "Kim cương";
            case "GOLD" -> "Vàng";
            case "SILVER" -> "Bạc";
            default -> "Đồng";
        };
    }
}