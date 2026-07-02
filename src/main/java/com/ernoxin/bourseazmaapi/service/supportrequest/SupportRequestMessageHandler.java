package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestMessage;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SupportRequestMessageHandler {

    private final SupportRequestRepository supportRequestRepository;
    private final SupportRequestMessageRepository supportRequestMessageRepository;
    private final SupportRequestTextNormalizer textNormalizer;

    public SupportRequestMessage createMessage(
            SupportRequest supportRequest,
            User author,
            UserRole authorRole,
            String rawMessage
    ) {
        String messageText = textNormalizer.normalizeRequiredText(rawMessage);
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

    public void touchRequest(SupportRequest supportRequest) {
        supportRequest.setUpdatedAt(Instant.now());
        supportRequestRepository.save(supportRequest);
    }

    public void markMessagesSeenByUser(SupportRequest request) {
        Instant now = Instant.now();
        List<SupportRequestMessage> messages =
                supportRequestMessageRepository.findAllBySupportRequestIdOrderByCreatedAtAsc(request.getId());
        for (SupportRequestMessage message : messages) {
            if (message.getAuthorRole() == UserRole.ADMIN && message.getSeenAt() == null) {
                message.setSeenAt(now);
                supportRequestMessageRepository.save(message);
            }
        }
    }

    public void markMessagesSeenByAdmin(SupportRequest request) {
        Instant now = Instant.now();
        if (request.getInitialMessageSeenAt() == null) {
            request.setInitialMessageSeenAt(now);
            supportRequestRepository.save(request);
        }
        List<SupportRequestMessage> messages =
                supportRequestMessageRepository.findAllBySupportRequestIdOrderByCreatedAtAsc(request.getId());
        for (SupportRequestMessage message : messages) {
            if (message.getAuthorRole() == UserRole.USER && message.getSeenAt() == null) {
                message.setSeenAt(now);
                supportRequestMessageRepository.save(message);
            }
        }
    }
}
