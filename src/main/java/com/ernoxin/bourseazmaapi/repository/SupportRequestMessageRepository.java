package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.SupportRequestMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupportRequestMessageRepository extends JpaRepository<SupportRequestMessage, Long> {
    List<SupportRequestMessage> findAllBySupportRequestIdOrderByCreatedAtAsc(Long supportRequestId);

    Optional<SupportRequestMessage> findByIdAndSupportRequestId(Long id, Long supportRequestId);

    @Query("""
            SELECT m.supportRequest.id AS supportRequestId,
                   COUNT(m) AS replyCount,
                   MAX(m.createdAt) AS lastReplyAt
            FROM SupportRequestMessage m
            WHERE m.supportRequest.id IN :supportRequestIds
            GROUP BY m.supportRequest.id
            """)
    List<SupportRequestMessageStats> aggregateStatsBySupportRequestIds(
            @Param("supportRequestIds") Collection<Long> supportRequestIds
    );
}
