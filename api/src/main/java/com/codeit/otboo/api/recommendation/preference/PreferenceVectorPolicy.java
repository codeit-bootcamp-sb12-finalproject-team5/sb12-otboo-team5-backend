package com.codeit.otboo.api.recommendation.preference;

public final class PreferenceVectorPolicy {

    public static final double SURVEY_VECTOR_WEIGHT = 3.0;
    public static final double OUTFIT_USAGE_WEIGHT = 0.36;
    public static final int MAX_OUTFIT_USAGE_COUNT = 20;

    public static final double PREFERENCE_1_WEIGHT = 0.0;
    public static final double PREFERENCE_2_WEIGHT = 0.0;
    public static final double PREFERENCE_3_WEIGHT = 0.25;
    public static final double PREFERENCE_4_WEIGHT = 0.6;
    public static final double PREFERENCE_5_WEIGHT = 1.0;

    private PreferenceVectorPolicy() {
    }

    public static double clothesWeight(Integer preference, long outfitUsageCount) {
        return preferenceWeight(preference)
            + OUTFIT_USAGE_WEIGHT * Math.log1p(Math.min(outfitUsageCount, MAX_OUTFIT_USAGE_COUNT));
    }

    private static double preferenceWeight(Integer preference) {
        if (preference == null) {
            return 0.0;
        }

        return switch (preference) {
            case 1 -> PREFERENCE_1_WEIGHT;
            case 2 -> PREFERENCE_2_WEIGHT;
            case 3 -> PREFERENCE_3_WEIGHT;
            case 4 -> PREFERENCE_4_WEIGHT;
            case 5 -> PREFERENCE_5_WEIGHT;
            default -> 0.0;
        };
    }
}
