package com.codeit.otboo.api.admin.dto;

import com.codeit.otboo.domain.user.entity.UserRole;
import java.util.UUID;

public record UserSearchCondition(
        String cursor,
        UUID idAfter,
        Integer limit,
        String sortBy,
        String sortDirection,
        String emailLike,
        UserRole roleEqual,
        Boolean locked
) {

    public int limitOrDefault() {
        return (limit == null || limit <= 0 || limit > 100) ? 20 : limit;
    }

    public String sortByOrDefault() {
        return (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
    }

    public String sortDirectionOrDefault() {
        return "ASCENDING".equalsIgnoreCase(sortDirection) ? "ASCENDING" : "DESCENDING";
    }
}
