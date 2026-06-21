package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.SupportRequestCreateRequest;
import com.ernoxin.bourseazmaapi.dto.SupportRequestResponse;
import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportRequestServiceImpl implements SupportRequestService {

    private final SupportRequestRepository supportRequestRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> getCurrentUserRequests() {
        Long userId = SecurityUtils.currentUserId();
        return supportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public SupportRequestResponse create(SupportRequestCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SupportRequest supportRequest = new SupportRequest();
        supportRequest.setUser(userRepository.getReferenceById(userId));
        supportRequest.setSubject(normalizeRequiredText(request.getSubject()));
        supportRequest.setMessage(normalizeRequiredText(request.getMessage()));
        return toDto(supportRequestRepository.save(supportRequest));
    }

    private SupportRequestResponse toDto(SupportRequest request) {
        return new SupportRequestResponse(
                request.getId(),
                request.getSubject(),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt().toString()
        );
    }

    private String normalizeRequiredText(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }
}
