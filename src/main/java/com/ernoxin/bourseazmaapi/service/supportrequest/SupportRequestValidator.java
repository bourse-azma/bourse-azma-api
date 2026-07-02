package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestMessage;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportRequestValidator {

    private final SupportRequestMessageRepository supportRequestMessageRepository;
    private final SupportRequestTextNormalizer textNormalizer;

    public void ensureUserCanReply(SupportRequest supportRequest) {
        if (textNormalizer.isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("این تیکت دیگر باز نیست و امکان ارسال پیام جدید وجود ندارد.");
        }
    }

    public void ensureAdminCanReply(SupportRequest supportRequest) {
        if (textNormalizer.isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("تیکت بسته شده است و امکان ارسال پیام جدید وجود ندارد.");
        }
    }

    public void ensureTicketOpenForUserEdit(SupportRequest supportRequest) {
        if (textNormalizer.isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("این تیکت بسته شده و امکان ویرایش پیام وجود ندارد.");
        }
    }

    public void ensureInitialMessageEditable(SupportRequest supportRequest) {
        if (supportRequest.getInitialMessageSeenAt() != null) {
            throw new IllegalArgumentException("این پیام توسط پشتیبانی مشاهده شده و دیگر قابل ویرایش نیست.");
        }
        if (supportRequestMessageRepository.existsBySupportRequestIdAndAuthorRole(
                supportRequest.getId(),
                UserRole.ADMIN
        )) {
            throw new IllegalArgumentException("پس از دریافت پاسخ پشتیبانی امکان ویرایش پیام اولیه وجود ندارد.");
        }
    }

    public void ensureMessageNotSeen(SupportRequestMessage message) {
        if (message.getSeenAt() != null) {
            throw new IllegalArgumentException("پیام‌های مشاهده‌شده دیگر قابل ویرایش نیستند.");
        }
    }

    public void validateStatusTransition(SupportRequestStatus current, SupportRequestStatus next) {
        if (current == next) {
            return;
        }
        if (current == SupportRequestStatus.CLOSED) {
            throw new IllegalArgumentException("تیکت بسته شده و وضعیت آن قابل تغییر نیست.");
        }
        boolean allowed = switch (current) {
            case OPEN -> next == SupportRequestStatus.IN_PROGRESS || next == SupportRequestStatus.CLOSED;
            case IN_PROGRESS -> next == SupportRequestStatus.CLOSED;
            case CLOSED, RESOLVED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("تغییر وضعیت از «" + current.name() + "» به «" + next.name() + "» مجاز نیست.");
        }
    }
}
