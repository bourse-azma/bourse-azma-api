package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    List<SupportRequest> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
