package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageStats;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupportRequestServiceImpl implements SupportRequestService {

    private static final long MESSAGE_EDIT_WINDOW_MINUTES = 10;
    private static final String SUPPORT_DISPLAY_NAME = "پشتیبانی";

    private final SupportRequestRepository supportRequestRepository;
    private final SupportRequestMessageRepository supportRequestMessageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> getCurrentUserRequests() {
        Long userId = SecurityUtils.currentUserId();
        List<SupportRequest> requests = supportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        return mapToSummaryList(requests, false);
    }

    @Override
    @Transactional
    public SupportRequestResponse create(SupportRequestCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.getReferenceById(userId);

        String subject = normalizeRequiredText(request.getSubject());
        String message = normalizeRequiredText(request.getMessage());
        if (subject.isEmpty() || message.isEmpty()) {
            throw new IllegalArgumentException("موضوع و متن پیام را کامل وارد کنید.");
        }

        SupportRequest supportRequest = new SupportRequest();
        supportRequest.setUser(user);
        supportRequest.setSubject(subject);
        supportRequest.setMessage(message);
        supportRequest.setCategory(request.getCategory());
        supportRequest.setPriority(request.getPriority());
        supportRequest.setStatus(SupportRequestStatus.OPEN);
        Instant now = Instant.now();
        supportRequest.setCreatedAt(now);
        supportRequest.setUpdatedAt(now);

        SupportRequest saved = supportRequestRepository.save(supportRequest);
        return toSummaryDto(saved, false, MessageStats.empty(saved.getCreatedAt()), false);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportRequestDetailResponse getCurrentUserRequestDetail(Long id) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest request = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        return toDetailDto(request, false, false);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse addUserMessage(Long id, SupportRequestMessageCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        ensureUserCanReply(supportRequest);

        User user = userRepository.getReferenceById(userId);
        SupportRequestMessage message = createMessage(supportRequest, user, UserRole.USER, request.getMessage());
        touchRequest(supportRequest);
        return toMessageDto(message, true);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse editUserMessage(Long id, Long messageId, SupportRequestMessageUpdateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        SupportRequestMessage message = supportRequestMessageRepository.findByIdAndSupportRequestId(messageId, id)
                .orElseThrow(() -> new ResourceNotFoundException("پیام مورد نظر یافت نشد."));
        if (!message.getAuthor().getId().equals(userId) || message.getAuthorRole() != UserRole.USER) {
            throw new IllegalArgumentException("فقط پیام‌های خودتان قابل ویرایش هستند.");
        }

        ensureTicketOpenForUserEdit(supportRequest);
        ensureEditableWithinWindow(message.getCreatedAt());
        message.setMessage(normalizeRequiredText(request.getMessage()));
        message.setEditedAt(Instant.now());
        SupportRequestMessage saved = supportRequestMessageRepository.save(message);
        touchRequest(supportRequest);
        return toMessageDto(saved, true);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse editUserInitialMessage(Long id, SupportRequestMessageUpdateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        ensureTicketOpenForUserEdit(supportRequest);
        ensureInitialMessageEditable(supportRequest);
        supportRequest.setMessage(normalizeRequiredText(request.getMessage()));
        supportRequest.setMessageEditedAt(Instant.now());
        supportRequest.setUpdatedAt(Instant.now());
        SupportRequest saved = supportRequestRepository.save(supportRequest);
        return toInitialMessageDto(saved, true);
    }

    @Override
    @Transactional
    public SupportRequestResponse closeCurrentUserRequest(Long id) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        return closeRequest(supportRequest, false, SupportRequestClosedBy.USER);
    }

    @Override
    @Transactional
    public SupportRequestResponse closeAdminRequest(Long id) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        return closeRequest(supportRequest, true, SupportRequestClosedBy.ADMIN);
    }

    @Override
    @Transactional
    public SupportRequestResponse rateRequest(Long id, SupportRequestRatingRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        if (supportRequest.getRating() != null) {
            throw new IllegalArgumentException("این تیکت قبلا امتیازدهی شده است.");
        }
        if (!isRateableStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("فقط تیکت‌های بسته‌شده قابل امتیازدهی هستند.");
        }

        supportRequest.setRating(request.getRating());
        if (request.getComment() != null && !request.getComment().isBlank()) {
            supportRequest.setRatingComment(normalizeOptionalText(request.getComment()));
        }
        supportRequest.setUpdatedAt(Instant.now());
        SupportRequest saved = supportRequestRepository.save(supportRequest);
        Map<Long, MessageStats> statsById = loadMessageStats(List.of(saved.getId()));
        return toSummaryDto(saved, false, statsFor(saved, statsById), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SupportRequestResponse> getAllRequests(
            SupportRequestStatus status,
            SupportRequestCategory category,
            SupportRequestPriority priority,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SupportRequest> result = supportRequestRepository.findAll(
                buildAdminFilterSpecification(status, category, priority),
                pageable
        );
        List<SupportRequestResponse> items = mapToSummaryList(result.getContent(), true);
        return new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SupportRequestDetailResponse getAdminRequestDetail(Long id) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        return toDetailDto(request, true, true);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse addAdminMessage(Long id, SupportRequestMessageCreateRequest request) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        ensureAdminCanReply(supportRequest);

        Long adminId = SecurityUtils.currentUserId();
        User admin = userRepository.getReferenceById(adminId);
        SupportRequestMessage message = createMessage(supportRequest, admin, UserRole.ADMIN, request.getMessage());

        if (supportRequest.getStatus() == SupportRequestStatus.OPEN) {
            supportRequest.setStatus(SupportRequestStatus.IN_PROGRESS);
        }
        touchRequest(supportRequest);
        return toMessageDto(message, false);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse editAdminMessage(Long id, Long messageId, SupportRequestMessageUpdateRequest request) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        Long adminId = SecurityUtils.currentUserId();
        SupportRequestMessage message = supportRequestMessageRepository.findByIdAndSupportRequestId(messageId, id)
                .orElseThrow(() -> new ResourceNotFoundException("پیام مورد نظر یافت نشد."));
        if (!message.getAuthor().getId().equals(adminId) || message.getAuthorRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("فقط پیام‌های خودتان قابل ویرایش هستند.");
        }

        ensureEditableWithinWindow(message.getCreatedAt());
        message.setMessage(normalizeRequiredText(request.getMessage()));
        message.setEditedAt(Instant.now());
        SupportRequestMessage saved = supportRequestMessageRepository.save(message);
        touchRequest(supportRequest);
        return toMessageDto(saved, false);
    }

    @Override
    @Transactional
    public SupportRequestResponse updateStatus(Long id, SupportRequestStatusUpdateRequest request) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        SupportRequestStatus newStatus = request.getStatus();
        if (newStatus == SupportRequestStatus.RESOLVED) {
            throw new IllegalArgumentException("وضعیت «بسته‌شده» تنها گزینه پایان کار تیکت است.");
        }
        supportRequest.setStatus(newStatus);
        Instant now = Instant.now();
        supportRequest.setUpdatedAt(now);
        if (newStatus == SupportRequestStatus.CLOSED) {
            supportRequest.setClosedAt(now);
            supportRequest.setClosedBy(SupportRequestClosedBy.ADMIN);
        } else {
            supportRequest.setClosedAt(null);
            supportRequest.setClosedBy(null);
        }

        SupportRequest saved = supportRequestRepository.save(supportRequest);
        Map<Long, MessageStats> statsById = loadMessageStats(List.of(saved.getId()));
        return toSummaryDto(saved, true, statsFor(saved, statsById), false);
    }

    private List<SupportRequestResponse> mapToSummaryList(List<SupportRequest> requests, boolean includeUser) {
        Map<Long, MessageStats> statsById = loadMessageStats(requests.stream().map(SupportRequest::getId).toList());
        return requests.stream()
                .map(request -> toSummaryDto(request, includeUser, statsFor(request, statsById), false))
                .toList();
    }

    private Map<Long, MessageStats> loadMessageStats(List<Long> supportRequestIds) {
        if (supportRequestIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MessageStats> statsById = new HashMap<>();
        for (SupportRequestMessageStats stats : supportRequestMessageRepository.aggregateStatsBySupportRequestIds(supportRequestIds)) {
            statsById.put(
                    stats.getSupportRequestId(),
                    new MessageStats(stats.getReplyCount(), stats.getLastReplyAt())
            );
        }
        return statsById;
    }

    private MessageStats statsFor(SupportRequest request, Map<Long, MessageStats> statsById) {
        MessageStats stats = statsById.get(request.getId());
        if (stats != null) {
            return stats;
        }
        return MessageStats.empty(request.getCreatedAt());
    }

    private Specification<SupportRequest> buildAdminFilterSpecification(
            SupportRequestStatus status,
            SupportRequestCategory category,
            SupportRequestPriority priority
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SupportRequestDetailResponse toDetailDto(SupportRequest request, boolean includeUser, boolean fullUserDetails) {
        List<SupportRequestMessage> threadMessages =
                supportRequestMessageRepository.findAllBySupportRequestIdOrderByCreatedAtAsc(request.getId());
        MessageStats stats = threadMessages.isEmpty()
                ? MessageStats.empty(request.getCreatedAt())
                : new MessageStats(
                threadMessages.size(),
                threadMessages.get(threadMessages.size() - 1).getCreatedAt()
        );
        boolean maskAuthorIdentity = !includeUser;
        List<SupportRequestMessageResponse> messages = buildConversation(request, threadMessages, maskAuthorIdentity);
        return new SupportRequestDetailResponse(toSummaryDto(request, includeUser, stats, fullUserDetails), messages);
    }

    private List<SupportRequestMessageResponse> buildConversation(
            SupportRequest request,
            List<SupportRequestMessage> threadMessages,
            boolean maskAuthorIdentity
    ) {
        List<SupportRequestMessageResponse> messages = new ArrayList<>();
        messages.add(toInitialMessageDto(request, maskAuthorIdentity));
        threadMessages.stream()
                .map(message -> toMessageDto(message, maskAuthorIdentity))
                .forEach(messages::add);
        return messages;
    }

    private SupportRequestResponse toSummaryDto(
            SupportRequest request,
            boolean includeUser,
            MessageStats stats,
            boolean fullUserDetails
    ) {
        return new SupportRequestResponse(
                request.getId(),
                request.getSubject(),
                request.getMessage(),
                normalizeStatus(request.getStatus()).name(),
                request.getCategory().name(),
                request.getPriority().name(),
                request.getRating(),
                request.getRatingComment(),
                (int) stats.replyCount() + 1,
                stats.lastReplyAt().toString(),
                request.getCreatedAt().toString(),
                request.getUpdatedAt().toString(),
                request.getClosedAt() == null ? null : request.getClosedAt().toString(),
                request.getClosedBy() == null ? null : request.getClosedBy().name(),
                includeUser ? toUserSummary(request.getUser(), fullUserDetails) : null
        );
    }

    private SupportRequestUserSummary toUserSummary(User user, boolean includeFullDetails) {
        return new SupportRequestUserSummary(
                user.getId(),
                displayName(user),
                user.getUsername(),
                includeFullDetails ? user.getFirstName() : null,
                includeFullDetails ? user.getLastName() : null,
                includeFullDetails ? user.getNationalCode() : null,
                includeFullDetails ? user.getPhoneNumber() : null,
                includeFullDetails ? user.getEmail() : null
        );
    }

    private SupportRequestMessageResponse toMessageDto(SupportRequestMessage message, boolean maskAuthorIdentity) {
        return new SupportRequestMessageResponse(
                message.getId(),
                message.getMessage(),
                message.getAuthorRole().name(),
                resolveAuthorName(message.getAuthor(), message.getAuthorRole(), maskAuthorIdentity),
                maskAuthorIdentity ? null : message.getAuthor().getId(),
                message.getCreatedAt().toString(),
                message.getEditedAt() == null ? null : message.getEditedAt().toString()
        );
    }

    private SupportRequestMessageResponse toInitialMessageDto(SupportRequest request, boolean maskAuthorIdentity) {
        return new SupportRequestMessageResponse(
                null,
                request.getMessage(),
                UserRole.USER.name(),
                resolveAuthorName(request.getUser(), UserRole.USER, maskAuthorIdentity),
                maskAuthorIdentity ? null : request.getUser().getId(),
                request.getCreatedAt().toString(),
                request.getMessageEditedAt() == null ? null : request.getMessageEditedAt().toString()
        );
    }

    private SupportRequestMessageResponse toInitialMessageDto(SupportRequest request) {
        return toInitialMessageDto(request, false);
    }

    private SupportRequestMessage createMessage(
            SupportRequest supportRequest,
            User author,
            UserRole authorRole,
            String rawMessage
    ) {
        String messageText = normalizeRequiredText(rawMessage);
        if (messageText.isEmpty()) {
            throw new IllegalArgumentException("متن پیام را کامل وارد کنید.");
        }

        SupportRequestMessage message = new SupportRequestMessage();
        message.setSupportRequest(supportRequest);
        message.setAuthor(author);
        message.setAuthorRole(authorRole);
        message.setMessage(messageText);
        message.setCreatedAt(Instant.now());
        return supportRequestMessageRepository.save(message);
    }

    private void touchRequest(SupportRequest supportRequest) {
        supportRequest.setUpdatedAt(Instant.now());
        supportRequestRepository.save(supportRequest);
    }

    private void ensureUserCanReply(SupportRequest supportRequest) {
        if (isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("این تیکت دیگر باز نیست و امکان ارسال پیام جدید وجود ندارد.");
        }
    }

    private void ensureAdminCanReply(SupportRequest supportRequest) {
        if (isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("تیکت بسته شده است و امکان ارسال پیام جدید وجود ندارد.");
        }
    }

    private void ensureTicketOpenForUserEdit(SupportRequest supportRequest) {
        if (isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("این تیکت بسته شده و امکان ویرایش پیام وجود ندارد.");
        }
    }

    private void ensureInitialMessageEditable(SupportRequest supportRequest) {
        if (supportRequestMessageRepository.existsBySupportRequestIdAndAuthorRole(
                supportRequest.getId(),
                UserRole.ADMIN
        )) {
            throw new IllegalArgumentException("پس از دریافت پاسخ پشتیبانی امکان ویرایش پیام اولیه وجود ندارد.");
        }
        ensureEditableWithinWindow(supportRequest.getCreatedAt());
    }

    private void ensureEditableWithinWindow(Instant createdAt) {
        Instant deadline = createdAt.plus(MESSAGE_EDIT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        if (Instant.now().isAfter(deadline)) {
            throw new IllegalArgumentException("فقط تا ۱۰ دقیقه پس از ارسال امکان ویرایش پیام وجود دارد.");
        }
    }

    private SupportRequestResponse closeRequest(
            SupportRequest supportRequest,
            boolean includeUser,
            SupportRequestClosedBy closedBy
    ) {
        if (isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("این تیکت قبلا بسته شده است.");
        }

        Instant now = Instant.now();
        supportRequest.setStatus(SupportRequestStatus.CLOSED);
        supportRequest.setUpdatedAt(now);
        supportRequest.setClosedAt(now);
        supportRequest.setClosedBy(closedBy);

        SupportRequest saved = supportRequestRepository.save(supportRequest);
        Map<Long, MessageStats> statsById = loadMessageStats(List.of(saved.getId()));
        return toSummaryDto(saved, includeUser, statsFor(saved, statsById), false);
    }

    private boolean isRateableStatus(SupportRequestStatus status) {
        return normalizeStatus(status) == SupportRequestStatus.CLOSED;
    }

    private boolean isClosedStatus(SupportRequestStatus status) {
        return status == SupportRequestStatus.CLOSED || status == SupportRequestStatus.RESOLVED;
    }

    private SupportRequestStatus normalizeStatus(SupportRequestStatus status) {
        return status == SupportRequestStatus.RESOLVED ? SupportRequestStatus.CLOSED : status;
    }

    private String resolveAuthorName(User user, UserRole authorRole, boolean maskAuthorIdentity) {
        if (maskAuthorIdentity && authorRole == UserRole.ADMIN) {
            return SUPPORT_DISPLAY_NAME;
        }
        return displayName(user);
    }

    private String displayName(User user) {
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }

    private String normalizeRequiredText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private record MessageStats(long replyCount, Instant lastReplyAt) {
        private static MessageStats empty(Instant createdAt) {
            return new MessageStats(0, createdAt);
        }
    }
}
