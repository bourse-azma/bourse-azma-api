package com.ernoxin.bourseazmaapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "support_request_messages")
public class SupportRequestMessage extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_request_id", nullable = false)
    private SupportRequest supportRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 10)
    private UserRole authorRole;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant editedAt;

    @Column
    private Instant seenAt;
}
