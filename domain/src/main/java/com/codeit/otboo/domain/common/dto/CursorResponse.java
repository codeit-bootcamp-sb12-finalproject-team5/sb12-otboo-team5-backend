package com.codeit.otboo.api.common.dto;

import java.util.List;

/**
 * 커서 기반 페이지네이션 공통 응답.
 * 모든 목록 조회 API 에서 재사용할 수 있습니다.
 *
 * 사용 예:
 *   CursorResponse<FeedDto> response = CursorResponse.of(
 *           feeds, nextCursor, nextIdAfter, hasNext, totalCount, sortBy, sortDirection);
 *
 * @param <T> 목록 요소 타입
 */
public record CursorResponse<T>(
        List<T> data,
        String nextCursor,
        Object nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        String sortDirection
) {

    public static <T> CursorResponse<T> of(List<T> data,
                                           String nextCursor,
                                           Object nextIdAfter,
                                           boolean hasNext,
                                           long totalCount,
                                           String sortBy,
                                           String sortDirection) {
        return new CursorResponse<>(
                data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy, sortDirection);
    }

    /** 다음 페이지가 없는 경우 */
    public static <T> CursorResponse<T> last(List<T> data,
                                             long totalCount,
                                             String sortBy,
                                             String sortDirection) {
        return new CursorResponse<>(
                data, null, null, false, totalCount, sortBy, sortDirection);
    }
}
