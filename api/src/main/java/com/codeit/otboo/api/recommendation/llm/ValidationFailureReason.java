package com.codeit.otboo.api.recommendation.llm;

enum ValidationFailureReason {
    UNKNOWN_CLOTHES_ID,      // 후보 목록에 없는 의상 ID 사용
    MISSING_SELECTED_CLOTHES, // 선택한 고정 의상 누락
    DUPLICATED_CLOTHES,      // 하나의 코디에 동일 의상 중복 사용
    INVALID_BASIC_OUTFIT,    // TOP + BOTTOM 또는 DRESS 기본 구성 위반
    INVALID_OPTIONAL_COUNT,  // 선택 카테고리(아우터·신발·모자·가방·액세서리) 중복 사용
    INVALID_CATEGORY,        // 허용되지 않은 카테고리 사용
    DUPLICATED_OUTFIT,       // 다른 추천 코디와 의상 구성이 동일함
    INVALID_RANK,            // 추천 순위가 1부터 연속되지 않거나 범위를 벗어남
    EMPTY_REASON,            // 추천 이유가 비어 있음
    RECENT_HISTORY_DUPLICATE // 최근 추천 이력과 동일한 코디
}
