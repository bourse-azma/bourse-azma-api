package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestCategory;
import com.ernoxin.bourseazmaapi.model.SupportRequestPriority;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SupportRequestQueryHelper {

    public Specification<SupportRequest> buildAdminFilterSpecification(
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
}
