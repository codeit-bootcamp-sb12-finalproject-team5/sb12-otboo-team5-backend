package com.codeit.otboo.api.feed;

import com.codeit.otboo.api.feed.dto.*;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.feed.entity.FeedComment;
import com.codeit.otboo.domain.feed.entity.FeedLike;
import com.codeit.otboo.domain.feed.enums.SortDirection;
import com.codeit.otboo.domain.feed.repository.FeedCommentRepository;
import com.codeit.otboo.domain.feed.repository.FeedLikeRepository;
import com.codeit.otboo.domain.feed.repository.FeedRepository;
import com.codeit.otboo.domain.outfit.entity.Ootd;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.repository.OotdRepository;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import com.codeit.otboo.support.storage.S3StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FeedRepository feedRepository;
    @Mock
    private FeedLikeRepository feedLikeRepository;
    @Mock
    private FeedCommentRepository feedCommentRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private OutfitRepository outfitRepository;
    @Mock
    private OotdRepository ootdRepository;
    @Mock
    private OutfitClothesRepository outfitClothesRepository;
    @Mock
    private ProfileRepository profileRepository;
    @InjectMocks
    private FeedService feedService;

    @Test
    void createsFeedWithTheOutfitIdAndReturnsItsClothes() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        User user = User.builder().id(userId).name("author").build();
        Outfit outfit = Outfit.builder().id(outfitId).user(user).category("OOTD").build();
        Ootd ootd = Ootd.builder()
            .id(outfitId)
            .outfit(outfit)
            .skyStatus(SkyStatus.CLEAR)
            .precipitationType(PrecipitationType.NONE)
            .precipitationAmount(new BigDecimal("0.00"))
            .precipitationProbability(new BigDecimal("10.00"))
            .temperatureCurrent(new BigDecimal("20.50"))
            .temperatureMin(new BigDecimal("15.00"))
            .temperatureMax(new BigDecimal("23.00"))
            .build();

        when(outfitRepository.findByIdAndDeletedAtIsNull(outfitId)).thenReturn(Optional.of(outfit));
        when(feedRepository.existsById(outfitId)).thenReturn(false);
        when(feedRepository.save(any(Feed.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outfitClothesRepository.findAllByOutfit_Id(outfitId)).thenReturn(List.of());
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(ootdRepository.findById(outfitId)).thenReturn(Optional.of(ootd));

        var response = feedService.create(userId, new FeedRequest(userId, outfitId, "오늘의 착장"));

        ArgumentCaptor<Feed> feedCaptor = ArgumentCaptor.forClass(Feed.class);
        org.mockito.Mockito.verify(feedRepository).save(feedCaptor.capture());
        assertThat(feedCaptor.getValue().getId()).isEqualTo(outfitId);
        assertThat(feedCaptor.getValue().getUser()).isSameAs(user);
        assertThat(response.id()).isEqualTo(outfitId);
        assertThat(response.author().userId()).isEqualTo(userId);
        assertThat(response.ootds()).isEmpty();
        assertThat(response.likedByMe()).isFalse();
        assertThat(response.weather().skyStatus()).isEqualTo(SkyStatus.CLEAR);
        assertThat(response.weather().temperature().current()).isEqualByComparingTo("20.50");
    }

    @Test
    void deletesCurrentUsersLikeAndDecreasesLikeCount() {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId)).thenReturn(1);
        when(feedRepository.decrementLikeCount(feedId)).thenReturn(1);

        feedService.deleteLike(userId, feedId);

        verify(feedLikeRepository).deleteByFeedIdAndUserId(feedId, userId);
        verify(feedRepository).decrementLikeCount(feedId);
    }

    @Test
    void marksFeedsLikedByTheCurrentUserInSearchResults() {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        User user = User.builder().id(userId).name("author").build();
        Outfit outfit = Outfit.builder().id(feedId).category("OUTFIT").user(user).build();
        Feed feed = Feed.builder().id(feedId).user(user).outfit(outfit).build();
        FeedLike feedLike = FeedLike.builder().feed(feed).user(user).build();
        FeedSearchRequest request = new FeedSearchRequest(
            null, null, 10, "createdAt", SortDirection.DESCENDING,
            null, null, null, null
        );
        when(feedRepository.findAllByDynamicQuery(
            null, null, 10, "createdAt", SortDirection.DESCENDING,
            null, null, null, null
        )).thenReturn(CursorResponse.last(List.of(feed), 1L, "createdAt", "DESCENDING"));
        when(feedLikeRepository.findAllByFeed_IdInAndUser_Id(List.of(feedId), userId))
            .thenReturn(List.of(feedLike));
        when(outfitClothesRepository.findAllByOutfit_IdIn(List.of(feedId))).thenReturn(List.of());
        when(profileRepository.findAllByUser_IdIn(List.of(userId))).thenReturn(List.of());
        when(ootdRepository.findAllByOutfit_IdIn(List.of(feedId))).thenReturn(List.of());

        var response = feedService.readAll(userId, request);

        assertThat(response.data()).singleElement()
            .extracting(feedResponse -> feedResponse.likedByMe())
            .isEqualTo(true);
    }

    @Test
    void createsCommentAndIncrementsCommentCount() {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        User user = User.builder().id(userId).name("author").build();
        Feed feed = Feed.builder().id(feedId).build();
        FeedCommentRequest request = new FeedCommentRequest(feedId, userId, "좋은 착장이에요");

        when(feedRepository.findByIdAndDeletedAtIsNull(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(feedCommentRepository.save(any(FeedComment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(feedRepository.incrementCommentCount(feedId)).thenReturn(1);
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        var response = feedService.createComment(userId, feedId, request);

        assertThat(response.feedId()).isEqualTo(feedId);
        assertThat(response.author().userId()).isEqualTo(userId);
        assertThat(response.content()).isEqualTo("좋은 착장이에요");
        verify(feedRepository).incrementCommentCount(feedId);
    }

    @Test
    void readsCommentsByFeedWithCursorMetadata() {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        User user = User.builder().id(userId).name("author").build();
        Feed feed = Feed.builder().id(feedId).build();
        FeedComment comment = FeedComment.builder()
            .id(UUID.randomUUID()).feed(feed).user(user).content("댓글").build();
        FeedCommentSearchRequest request = new FeedCommentSearchRequest(feedId, null, null, 10);

        when(feedRepository.findByIdAndDeletedAtIsNull(feedId)).thenReturn(Optional.of(feed));
        when(feedCommentRepository.findAllByFeedId(
            org.mockito.ArgumentMatchers.eq(feedId), any()
        )).thenReturn(List.of(comment));
        when(profileRepository.findAllByUser_IdIn(List.of(userId))).thenReturn(List.of());
        when(feedCommentRepository.countByFeed_Id(feedId)).thenReturn(1L);

        var response = feedService.readAllComment(feedId, request);

        assertThat(response.data()).singleElement()
            .extracting(commentResponse -> commentResponse.content())
            .isEqualTo("댓글");
        assertThat(response.totalCount()).isEqualTo(1L);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void updatesOwnedFeedAndReturnsTheChangedResponse() {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        User user = User.builder().id(userId).name("author").build();
        Outfit outfit = Outfit.builder().id(feedId).category("OUTFIT").user(user).build();
        Feed feed = Feed.builder()
            .id(feedId).user(user).outfit(outfit).content("before").isVisible(true).build();

        when(feedRepository.findByIdAndDeletedAtIsNull(feedId)).thenReturn(Optional.of(feed));
        when(feedLikeRepository.existsByFeed_IdAndUser_Id(feedId, userId)).thenReturn(false);
        when(outfitClothesRepository.findAllByOutfit_Id(feedId)).thenReturn(List.of());
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        var response = feedService.update(userId, feedId, new FeedUpdateRequest("after", false));

        assertThat(response.content()).isEqualTo("after");
    }

    @Test
    void softDeletesOwnedFeed() {
        UUID userId = UUID.randomUUID();
        UUID feedId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        Feed feed = Feed.builder().id(feedId).user(user).build();
        when(feedRepository.findByIdAndDeletedAtIsNull(feedId)).thenReturn(Optional.of(feed));

        feedService.softDelete(userId, feedId);

        assertThat(feed.isDeleted()).isTrue();
    }
}
