package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import org.springframework.stereotype.Component;

@Component
public class SupportRequestTextNormalizer {

    public String normalizeRequiredText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public String normalizeOptionalText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    public SupportRequestStatus normalizeIncomingStatus(SupportRequestStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("وضعیت تیکت مشخص نشده است.");
        }
        return status == SupportRequestStatus.RESOLVED ? SupportRequestStatus.CLOSED : status;
    }

    public SupportRequestStatus normalizeStatus(SupportRequestStatus status) {
        return status == SupportRequestStatus.RESOLVED ? SupportRequestStatus.CLOSED : status;
    }

    public boolean isRateableStatus(SupportRequestStatus status) {
        return normalizeStatus(status) == SupportRequestStatus.CLOSED;
    }

    public boolean isClosedStatus(SupportRequestStatus status) {
        return status == SupportRequestStatus.CLOSED || status == SupportRequestStatus.RESOLVED;
    }
}
