package com.codeit.otboo.api.admin.service;

import com.codeit.otboo.api.admin.dto.UserSearchCondition;
import com.codeit.otboo.domain.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 사용자 검색 조건 조립.
 * 값이 있는 조건만 WHERE 절에 추가하므로 null 파라미터 문제가 발생하지 않습니다.
 */
public final class UserSpecification {

    private UserSpecification() {
    }

    public static Specification<User> from(UserSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isNull(root.get("deletedAt")));

            if (condition.emailLike() != null && !condition.emailLike().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("email")),
                        "%" + condition.emailLike().toLowerCase() + "%"));
            }

            if (condition.roleEqual() != null) {
                predicates.add(cb.equal(root.get("role"), condition.roleEqual()));
            }

            if (condition.locked() != null) {
                predicates.add(cb.equal(root.get("locked"), condition.locked()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
