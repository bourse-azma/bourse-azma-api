package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import com.ernoxin.bourseazmaapi.service.supportrequest.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupportRequestServiceImpl implements SupportRequestService {

    private final SupportRequestRepository supportRequestRepository;
    private final SupportRequestMessageRepository supportRequestMessageRepository;
    private final UserRepository userRepository;
    private final SupportRequestDtoMapper dtoMapper;
    private final SupportRequestMessageHandler messageHandler;
    private final SupportRequestValidator validator;
    private final SupportRequestStateHandler stateHandler;
    private final SupportRequestTextNormalizer textNormalizer;
    private final SupportRequestStatsLoader statsLoader;
    private final SupportRequestQueryHelper queryHelper;

    @Override
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> getCurrentUserRequests() {
        Long userId = SecurityUtils.currentUserId();
        List<SupportRequest> requests = supportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        return dtoMapper.mapToSummaryList(requests, false);
    }

    @Override
    @Transactional
    public SupportRequestResponse create(SupportRequestCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.getReferenceById(userId);

        String subject = textNormalizer.normalizeRequiredText(request.getSubject());
        String message = textNormalizer.normalizeRequiredText(request.getMessage());
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
        return dtoMapper.toSummaryDto(saved, false, SupportRequestStatsLoader.MessageStats.empty(saved.getCreatedAt()), false);
    }

    @Override
    @Transactional
    public SupportRequestDetailResponse getCurrentUserRequestDetail(Long id) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest request = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        messageHandler.markMessagesSeenByUser(request);
        return dtoMapper.toDetailDto(request, false, false);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse addUserMessage(Long id, SupportRequestMessageCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        validator.ensureUserCanReply(supportRequest);

        User user = userRepository.getReferenceById(userId);
        SupportRequestMessage message = messageHandler.createMessage(supportRequest, user, UserRole.USER, request.getMessage());
        messageHandler.touchRequest(supportRequest);
        return dtoMapper.toMessageDto(message, true);
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

        validator.ensureTicketOpenForUserEdit(supportRequest);
        validator.ensureMessageNotSeen(message);
        message.setMessage(textNormalizer.normalizeRequiredText(request.getMessage()));
        message.setEditedAt(Instant.now());
        SupportRequestMessage saved = supportRequestMessageRepository.save(message);
        messageHandler.touchRequest(supportRequest);
        return dtoMapper.toMessageDto(saved, true);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse editUserInitialMessage(Long id, SupportRequestMessageUpdateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        validator.ensureTicketOpenForUserEdit(supportRequest);
        validator.ensureInitialMessageEditable(supportRequest);
        supportRequest.setMessage(textNormalizer.normalizeRequiredText(request.getMessage()));
        supportRequest.setMessageEditedAt(Instant.now());
        supportRequest.setUpdatedAt(Instant.now());
        SupportRequest saved = supportRequestRepository.save(supportRequest);
        return dtoMapper.toInitialMessageDto(saved, true);
    }

    @Override
    @Transactional
    public SupportRequestResponse closeCurrentUserRequest(Long id) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = supportRequestRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        return stateHandler.closeRequest(supportRequest, false, SupportRequestClosedBy.USER);
    }

    @Override
    @Transactional
    public SupportRequestResponse closeAdminRequest(Long id) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        return stateHandler.closeRequest(supportRequest, true, SupportRequestClosedBy.ADMIN);
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
        if (!textNormalizer.isRateableStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("فقط تیکت‌های بسته‌شده قابل امتیازدهی هستند.");
        }

        supportRequest.setRating(request.getRating());
        if (request.getComment() != null && !request.getComment().isBlank()) {
            supportRequest.setRatingComment(textNormalizer.normalizeOptionalText(request.getComment()));
        }
        supportRequest.setUpdatedAt(Instant.now());
        SupportRequest saved = supportRequestRepository.save(supportRequest);
        Map<Long, SupportRequestStatsLoader.MessageStats> statsById = statsLoader.loadMessageStats(List.of(saved.getId()));
        return dtoMapper.toSummaryDto(saved, false, statsLoader.statsFor(saved, statsById), false);
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
                queryHelper.buildAdminFilterSpecification(status, category, priority),
                pageable
        );
        List<SupportRequestResponse> items = dtoMapper.mapToSummaryList(result.getContent(), true);
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
    @Transactional
    public SupportRequestDetailResponse getAdminRequestDetail(Long id) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        messageHandler.markMessagesSeenByAdmin(request);
        return dtoMapper.toDetailDto(request, true, true);
    }

    @Override
    @Transactional
    public SupportRequestMessageResponse addAdminMessage(Long id, SupportRequestMessageCreateRequest request) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));
        validator.ensureAdminCanReply(supportRequest);

        Long adminId = SecurityUtils.currentUserId();
        User admin = userRepository.getReferenceById(adminId);
        SupportRequestMessage message = messageHandler.createMessage(supportRequest, admin, UserRole.ADMIN, request.getMessage());

        if (supportRequest.getStatus() == SupportRequestStatus.OPEN) {
            supportRequest.setStatus(SupportRequestStatus.IN_PROGRESS);
        }
        messageHandler.touchRequest(supportRequest);
        return dtoMapper.toMessageDto(message, false);
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

        validator.ensureMessageNotSeen(message);
        message.setMessage(textNormalizer.normalizeRequiredText(request.getMessage()));
        message.setEditedAt(Instant.now());
        SupportRequestMessage saved = supportRequestMessageRepository.save(message);
        messageHandler.touchRequest(supportRequest);
        return dtoMapper.toMessageDto(saved, false);
    }

    @Override
    @Transactional
    public SupportRequestResponse updateStatus(Long id, SupportRequestStatusUpdateRequest request) {
        SupportRequest supportRequest = supportRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تیکت مورد نظر یافت نشد."));

        SupportRequestStatus newStatus = textNormalizer.normalizeIncomingStatus(request.getStatus());
        SupportRequestStatus currentStatus = textNormalizer.normalizeStatus(supportRequest.getStatus());
        validator.validateStatusTransition(currentStatus, newStatus);

        if (newStatus == SupportRequestStatus.CLOSED) {
            stateHandler.applyClosedStatus(supportRequest, SupportRequestClosedBy.ADMIN);
        } else {
            supportRequest.setStatus(newStatus);
            supportRequest.setUpdatedAt(Instant.now());
            stateHandler.clearClosedMetadata(supportRequest);
        }

        SupportRequest saved = supportRequestRepository.save(supportRequest);
        Map<Long, SupportRequestStatsLoader.MessageStats> statsById = statsLoader.loadMessageStats(List.of(saved.getId()));
        return dtoMapper.toSummaryDto(saved, true, statsLoader.statsFor(saved, statsById), false);
    }
}
