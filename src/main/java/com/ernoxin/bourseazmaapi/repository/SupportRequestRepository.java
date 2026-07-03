package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestCategory;
import com.ernoxin.bourseazmaapi.model.SupportRequestPriority;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
