package com.codeit.otboo.api.recommendation.llm;

//todo: 한국어 설명 추가
enum ValidationFailureReason {
    UNKNOWN_CLOTHES_ID,
    DUPLICATED_CLOTHES,
    INVALID_TOP_COUNT,
    INVALID_BOTTOM_COUNT,
    INVALID_OUTER_COUNT,
    INVALID_SHOES_COUNT,
    INVALID_CATEGORY,
    DUPLICATED_OUTFIT,
    INVALID_RANK,
    EMPTY_REASON
}
