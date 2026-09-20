package com.codeit.otboo.domain.feed.repository;

import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.feed.enums.PrecipitationType;
import com.codeit.otboo.domain.feed.enums.SkyStatus;
import com.codeit.otboo.domain.feed.enums.SortDirection;

import java.util.UUID;

public interface FeedQueryRepository {

    CursorResponse<Feed> findAllByDynamicQuery(
            String cursor,
            UUID idAfter,
            Integer limit,
            String sortBy,
            SortDirection sortDirection,
            String keywordLike,
            SkyStatus skyStatusEqual,
            PrecipitationType precipitationTypeEqual,
            UUID authorIdEqual
    );

}
