package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "support_requests")
public class SupportRequest extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRequestStatus status = SupportRequestStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRequestCategory category = SupportRequestCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRequestPriority priority = SupportRequestPriority.MEDIUM;

    @Column
    private Integer rating;

    @Column(length = 500)
    private String ratingComment;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SupportRequestClosedBy closedBy;

    @Column
    private Instant messageEditedAt;

    @Column
    private Instant initialMessageSeenAt;
}
