package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByRole(UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    long countByRole(UserRole role);

    long countByRoleAndUsernameNotAndDeletedAtIsNull(UserRole role, String excludedUsername);

    long countByRoleAndLastSeenAtAfter(UserRole role, Instant threshold);

    long countByRoleAndUsernameNotAndDeletedAtIsNullAndBlockedFalseAndLastSeenAtAfter(
            UserRole role, String excludedUsername, Instant threshold);

    long countByRoleAndCreatedAtAfter(UserRole role, Instant threshold);

    long countByRoleAndUsernameNotAndDeletedAtIsNullAndCreatedAtAfter(UserRole role, String excludedUsername, Instant threshold);

    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :seenAt WHERE u.id = :userId")
    void updateLastSeenAt(@Param("userId") Long userId, @Param("seenAt") Instant seenAt);

    @Modifying
    @Query(value = """
            UPDATE users u SET created_at = COALESCE(
                (SELECT MIN(w.created_at) FROM wallet_transactions w WHERE w.user_id = u.id),
                :createdAt
            ) WHERE u.created_at IS NULL
            """, nativeQuery = true)
    int backfillMissingCreatedAt(@Param("createdAt") Instant createdAt);
}
