package com.codeit.otboo.api.recommendation.history;

/** 최근 추천 이력 기반 다양성 정책 상수입니다. */
public final class RecommendationHistoryPolicy {
    public static final int RECENT_HISTORY_LIMIT = 10;
    public static final int STRONG_HISTORY_WINDOW = 3;
    public static final double STRONG_HISTORY_PENALTY = 0.15;
    public static final double WEAK_HISTORY_PENALTY = 0.05;

    private RecommendationHistoryPolicy() {
    }
}
