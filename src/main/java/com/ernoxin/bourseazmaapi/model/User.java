package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "users")
public class User extends BaseEntity<Long> {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$")
    @Column(unique = true)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    private String lastName;

    @Pattern(regexp = "^\\d{10}$")
    @Column(unique = true)
    private String nationalCode;

    @Pattern(regexp = "^\\+98\\d{10}$")
    @Column(unique = true)
    private String phoneNumber;

    @Email
    @Column(unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, columnDefinition = "numeric(38,2) default 0")
    private java.math.BigDecimal balance = java.math.BigDecimal.ZERO;

    @Column
    private Instant createdAt;

    private Instant lastLoginAt;

    private Instant lastSeenAt;

    @Column(length = 64)
    private String lastLoginIp;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blocked = false;

    private Instant blockedAt;

    @Column(length = 500)
    private String blockedReason;

    private Instant deletedAt;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long tokenVersion = 0L;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
