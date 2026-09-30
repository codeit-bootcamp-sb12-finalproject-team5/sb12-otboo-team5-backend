package com.codeit.otboo.api.feed.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.otboo.domain.feed.enums.SortDirection;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeedSearchRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsUnsupportedSortByAndNonPositiveLimit() {
        FeedSearchRequest request = new FeedSearchRequest(
            null, null, 0, "content", SortDirection.DESCENDING,
            null, null, null, null
        );

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("limit", "sortBy");
    }
}
