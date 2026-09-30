package com.codeit.otboo.api.clothes;

record BulkClothesProcessResult(
        int index,
        String url,
        boolean success,
        String failureReason
) {

    static BulkClothesProcessResult success(int index, String url) {
        return new BulkClothesProcessResult(index, url, true, null);
    }

    static BulkClothesProcessResult failure(int index, String url, String failureReason) {
        return new BulkClothesProcessResult(index, url, false, failureReason);
    }
}
