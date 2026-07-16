package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestCategory;
import com.ernoxin.bourseazmaapi.model.SupportRequestPriority;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long>, JpaSpecificationExecutor<SupportRequest> {
    long countByUserId(Long userId);

    long countByStatusNot(SupportRequestStatus status);

    long countByStatusIn(Collection<SupportRequestStatus> statuses);

    List<SupportRequest> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SupportRequest> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM SupportRequest request WHERE request.id = :id")
    Optional<SupportRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM SupportRequest request " +
            "WHERE request.id = :id AND request.user.id = :userId")
    Optional<SupportRequest> findByIdAndUserIdForUpdate(@Param("id") Long id,
                                                        @Param("userId") Long userId);

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByStatusOrderByCreatedAtDesc(SupportRequestStatus status);

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByCategoryOrderByCreatedAtDesc(SupportRequestCategory category);

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByPriorityOrderByCreatedAtDesc(SupportRequestPriority priority);

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByStatusAndCategoryOrderByCreatedAtDesc(
            SupportRequestStatus status,
            SupportRequestCategory category
    );

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByStatusAndPriorityOrderByCreatedAtDesc(
            SupportRequestStatus status,
            SupportRequestPriority priority
    );

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByCategoryAndPriorityOrderByCreatedAtDesc(
            SupportRequestCategory category,
            SupportRequestPriority priority
    );

    @EntityGraph(attributePaths = "user")
    List<SupportRequest> findAllByStatusAndCategoryAndPriorityOrderByCreatedAtDesc(
            SupportRequestStatus status,
            SupportRequestCategory category,
            SupportRequestPriority priority
    );

    List<SupportRequest> findAllByStatusInAndUpdatedAtBefore(
            Collection<SupportRequestStatus> statuses,
            Instant updatedAtBefore
    );
}
