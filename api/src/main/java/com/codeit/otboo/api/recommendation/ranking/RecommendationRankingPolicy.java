package com.codeit.otboo.api.recommendation.ranking;

/** ranking 정책 상수를 한 곳에서 관리합니다. */
public final class RecommendationRankingPolicy {

    public static final int VECTOR_DIMENSION = 1536;
    public static final double SIMILARITY_WEIGHT = 0.8;
    public static final double PREFERENCE_WEIGHT = 0.2;
    public static final double NEUTRAL_PREFERENCE = 0.5;
    public static final double PREFERENCE_MAX = 5.0;

    private RecommendationRankingPolicy() {
    }
}
