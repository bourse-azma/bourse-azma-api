package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.dto.SupportRequestDetailResponse;
import com.ernoxin.bourseazmaapi.dto.SupportRequestMessageResponse;
import com.ernoxin.bourseazmaapi.dto.SupportRequestResponse;
import com.ernoxin.bourseazmaapi.dto.SupportRequestUserSummary;
import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestMessage;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SupportRequestDtoMapper {

    private static final String SUPPORT_DISPLAY_NAME = "پشتیبانی";

    private final SupportRequestMessageRepository supportRequestMessageRepository;
    private final SupportRequestStatsLoader statsLoader;
    private final SupportRequestTextNormalizer textNormalizer;

    public List<SupportRequestResponse> mapToSummaryList(List<SupportRequest> requests, boolean includeUser) {
        Map<Long, SupportRequestStatsLoader.MessageStats> statsById =
                statsLoader.loadMessageStats(requests.stream().map(SupportRequest::getId).toList());
        return requests.stream()
                .map(request -> toSummaryDto(request, includeUser, statsLoader.statsFor(request, statsById), false))
                .toList();
    }

    public SupportRequestDetailResponse toDetailDto(SupportRequest request, boolean includeUser, boolean fullUserDetails) {
        List<SupportRequestMessage> threadMessages =
                supportRequestMessageRepository.findAllBySupportRequestIdOrderByCreatedAtAsc(request.getId());
        SupportRequestStatsLoader.MessageStats stats = threadMessages.isEmpty()
                ? SupportRequestStatsLoader.MessageStats.empty(request.getCreatedAt())
                : new SupportRequestStatsLoader.MessageStats(
                threadMessages.size(),
                threadMessages.get(threadMessages.size() - 1).getCreatedAt()
        );
        boolean maskAuthorIdentity = !includeUser;
        List<SupportRequestMessageResponse> messages = buildConversation(request, threadMessages, maskAuthorIdentity);
        return new SupportRequestDetailResponse(toSummaryDto(request, includeUser, stats, fullUserDetails), messages);
    }

    public List<SupportRequestMessageResponse> buildConversation(
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

    public SupportRequestResponse toSummaryDto(
            SupportRequest request,
            boolean includeUser,
            SupportRequestStatsLoader.MessageStats stats,
            boolean fullUserDetails
    ) {
        return new SupportRequestResponse(
                request.getId(),
                request.getSubject(),
                request.getMessage(),
                textNormalizer.normalizeStatus(request.getStatus()).name(),
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

    public SupportRequestUserSummary toUserSummary(User user, boolean includeFullDetails) {
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

    public SupportRequestMessageResponse toMessageDto(SupportRequestMessage message, boolean maskAuthorIdentity) {
        return new SupportRequestMessageResponse(
                message.getId(),
                message.getMessage(),
                message.getAuthorRole().name(),
                resolveAuthorName(message.getAuthor(), message.getAuthorRole(), maskAuthorIdentity),
                maskAuthorIdentity ? null : message.getAuthor().getId(),
                message.getCreatedAt().toString(),
                message.getEditedAt() == null ? null : message.getEditedAt().toString(),
                message.getSeenAt() == null ? null : message.getSeenAt().toString()
        );
    }

    public SupportRequestMessageResponse toInitialMessageDto(SupportRequest request, boolean maskAuthorIdentity) {
        return new SupportRequestMessageResponse(
                null,
                request.getMessage(),
                UserRole.USER.name(),
                resolveAuthorName(request.getUser(), UserRole.USER, maskAuthorIdentity),
                maskAuthorIdentity ? null : request.getUser().getId(),
                request.getCreatedAt().toString(),
                request.getMessageEditedAt() == null ? null : request.getMessageEditedAt().toString(),
                request.getInitialMessageSeenAt() == null ? null : request.getInitialMessageSeenAt().toString()
        );
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
}
