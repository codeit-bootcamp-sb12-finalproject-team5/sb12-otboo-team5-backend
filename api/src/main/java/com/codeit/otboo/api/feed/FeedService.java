package com.codeit.otboo.api.feed;

import com.codeit.otboo.api.feed.dto.*;
import com.codeit.otboo.api.notification.event.NotificationEvents;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.entity.OutfitClothes;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.feed.entity.FeedComment;
import com.codeit.otboo.domain.feed.entity.FeedLike;
import com.codeit.otboo.domain.feed.exception.FeedException;
import com.codeit.otboo.domain.feed.repository.FeedLikeRepository;
import com.codeit.otboo.domain.feed.repository.FeedCommentRepository;
import com.codeit.otboo.domain.feed.repository.FeedRepository;
import com.codeit.otboo.domain.outfit.entity.Ootd;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.exception.OutfitException;
import com.codeit.otboo.domain.outfit.repository.OotdRepository;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedService {
    private final UserRepository userRepository;
    private final FeedRepository feedRepository;
    private final OutfitRepository outfitRepository;
    private final OotdRepository ootdRepository;
    private final OutfitClothesRepository outfitClothesRepository;
    private final ProfileRepository profileRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final S3StorageService s3StorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FeedResponse create(UUID authenticatedUserId, FeedRequest request) {
        if (!authenticatedUserId.equals(request.authorId())) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(request.outfitId())
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));
        if (!outfit.getUser().getId().equals(authenticatedUserId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }
        if (feedRepository.existsById(outfit.getId())) {
            throw new FeedException(ErrorCode.FEED_ALREADY_EXISTS);
        }

        Feed feed = feedRepository.save(Feed.builder()
            .id(outfit.getId())
            .user(outfit.getUser())
            .content(request.content())
            .isVisible(true)
            .likeCount(0L)
            .commentCount(0L)
            .build());

        List<Clothes> clothes = outfitClothesRepository.findAllByOutfit_Id(outfit.getId()).stream()
            .map(OutfitClothes::getClothes)
            .toList();
        String profileImageUrl = profileRepository.findByUser_Id(authenticatedUserId)
            .map(Profile::getProfileImageUrl)
            .orElse(null);
        Ootd ootd = "OOTD".equalsIgnoreCase(outfit.getCategory())
            ? ootdRepository.findById(outfit.getId()).orElse(null)
            : null;

        return FeedResponse.of(
            feed,
            clothes,
            s3StorageService.getPresignedUrl(profileImageUrl),
            ootd,
            false,
            clothesItem -> s3StorageService.getPresignedUrl(clothesItem.getImageUrl())
        );
    }

    @Transactional(readOnly = true)
    public CursorResponse<FeedResponse> readAll(UUID currentUserId, FeedSearchRequest req) {
        CursorResponse<Feed> feeds = feedRepository.findAllByDynamicQuery(
            req.cursor(),
            req.idAfter(),
            req.limit(),
            req.sortBy(),
            req.sortDirection(),
            req.keywordLike(),
            req.skyStatusEqual(),
            req.precipitationTypeEqual(),
            req.authorIdEqual()
        );

        List<UUID> outfitIds = feeds.data().stream().map(Feed::getId).toList();
        Set<UUID> likedFeedIds = outfitIds.isEmpty()
            ? Set.of()
            : feedLikeRepository.findAllByFeed_IdInAndUser_Id(outfitIds, currentUserId).stream()
                .map(feedLike -> feedLike.getFeed().getId())
                .collect(Collectors.toSet());
        Map<UUID, List<Clothes>> clothesByOutfitId = outfitIds.isEmpty()
            ? Map.of()
            : outfitClothesRepository.findAllByOutfit_IdIn(outfitIds).stream()
                .collect(Collectors.groupingBy(
                    outfitClothes -> outfitClothes.getOutfit().getId(),
                    Collectors.mapping(OutfitClothes::getClothes, Collectors.toList())
                ));
        Map<UUID, String> profileImageUrlByUserId = feeds.data().isEmpty()
            ? Map.of()
            : profileRepository.findAllByUser_IdIn(feeds.data().stream()
                    .map(feed -> feed.getUser().getId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                    profile -> profile.getUser().getId(),
                    Profile::getProfileImageUrl
                ));
        Map<UUID, Ootd> ootdByOutfitId = outfitIds.isEmpty()
            ? Map.of()
            : ootdRepository.findAllByOutfit_IdIn(outfitIds).stream()
                .collect(Collectors.toMap(ootd -> ootd.getOutfit().getId(), Function.identity()));

        List<FeedResponse> data = feeds.data().stream()
            .map(feed -> FeedResponse.of(
                feed,
                clothesByOutfitId.getOrDefault(feed.getId(), List.of()),
                s3StorageService.getPresignedUrl(profileImageUrlByUserId.get(feed.getUser().getId())),
                "OOTD".equalsIgnoreCase(feed.getOutfit().getCategory())
                    ? ootdByOutfitId.get(feed.getId())
                    : null,
                likedFeedIds.contains(feed.getId()),
                clothes -> s3StorageService.getPresignedUrl(clothes.getImageUrl())
            ))
            .toList();

        return CursorResponse.of(
            data,
            feeds.nextCursor(),
            feeds.nextIdAfter(),
            feeds.hasNext(),
            feeds.totalCount(),
            feeds.sortBy(),
            feeds.sortDirection()
        );
    }

    @Transactional
    public FeedResponse update(UUID userId, UUID feedId, FeedUpdateRequest request) {
        Feed feed = getOwnedFeed(userId, feedId);
        feed.update(request.content(), request.isVisible());
        return toFeedResponse(feed, feedLikeRepository.existsByFeed_IdAndUser_Id(feedId, userId));
    }

    @Transactional
    public void softDelete(UUID userId, UUID feedId) {
        getOwnedFeed(userId, feedId).markDeleted();
    }

    @Transactional
    public void createLike(UUID userId, UUID feedId) {
        Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
                .orElseThrow(() -> new FeedException(ErrorCode.FEED_NOT_FOUND));
        FeedLike like;
        try {
            like = feedLikeRepository.saveAndFlush(
                FeedLike.builder().feed(feed).user(getCurrentUserEntity(userId)).build());
        } catch (DataIntegrityViolationException exception) {
            throw new FeedException(ErrorCode.FEED_ALREADY_LIKED);
        }
        if (feedRepository.incrementLikeCount(feedId) == 0) {
            throw new FeedException(ErrorCode.FEED_NOT_FOUND);
        }

        if (!feed.getUser().getId().equals(userId)) {
            eventPublisher.publishEvent(NotificationEvents.feedLiked(like.getId()));
        }
    }

    @Transactional
    public void deleteLike(UUID userId, UUID feedId) {
        if (feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId) == 0) {
            throw new FeedException(ErrorCode.FEED_LIKE_NOT_FOUND);
        }
        if (feedRepository.decrementLikeCount(feedId) == 0) {
            throw new FeedException(ErrorCode.FEED_NOT_FOUND);
        }
    }

    @Transactional
    public FeedCommentResponse createComment(
        UUID authenticatedUserId,
        UUID pathFeedId,
        FeedCommentRequest request
    ) {
        validateCommentRequestIds(authenticatedUserId, pathFeedId, request.feedId(), request.authorId());

        Feed feed = feedRepository.findByIdAndDeletedAtIsNull(pathFeedId)
            .orElseThrow(() -> new FeedException(ErrorCode.FEED_NOT_FOUND));
        User author = getCurrentUserEntity(authenticatedUserId);
        FeedComment comment = feedCommentRepository.save(FeedComment.builder()
            .feed(feed)
            .user(author)
            .content(request.content())
            .build());
        if (feedRepository.incrementCommentCount(feed.getId()) == 0) {
            throw new FeedException(ErrorCode.FEED_NOT_FOUND);
        }

        if (!feed.getUser().getId().equals(author.getId())) {
            eventPublisher.publishEvent(NotificationEvents.commentCreated(comment.getId()));
        }

        String profileImageUrl = profileRepository.findByUser_Id(author.getId())
            .map(Profile::getProfileImageUrl)
            .map(s3StorageService::getPresignedUrl)
            .orElse(null);
        return FeedCommentResponse.of(comment, profileImageUrl);
    }

    @Transactional(readOnly = true)
    public CursorResponse<FeedCommentResponse> readAllComment(
        UUID pathFeedId,
        FeedCommentSearchRequest request
    ) {
        if (!pathFeedId.equals(request.feedId())) {
            throw new FeedException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (feedRepository.findByIdAndDeletedAtIsNull(pathFeedId).isEmpty()) {
            throw new FeedException(ErrorCode.FEED_NOT_FOUND);
        }

        OffsetDateTime cursor = parseCommentCursor(request.cursor());
        PageRequest pageRequest = PageRequest.of(0, request.limit() + 1);
        List<FeedComment> comments;
        if (cursor == null) {
            comments = feedCommentRepository.findAllByFeedId(pathFeedId, pageRequest);
        } else if (request.idAfter() == null) {
            comments = feedCommentRepository.findAllByFeedIdAfterCursor(
                pathFeedId, cursor, pageRequest);
        } else {
            comments = feedCommentRepository.findAllByFeedIdAfterCursorAndId(
                pathFeedId, cursor, request.idAfter(), pageRequest);
        }
        boolean hasNext = comments.size() > request.limit();
        if (hasNext) {
            comments = comments.subList(0, request.limit());
        }

        Map<UUID, String> profileImageUrlByUserId = comments.isEmpty()
            ? Map.of()
            : profileRepository.findAllByUser_IdIn(comments.stream()
                    .map(comment -> comment.getUser().getId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                    profile -> profile.getUser().getId(),
                    profile -> s3StorageService.getPresignedUrl(profile.getProfileImageUrl())
                ));
        List<FeedCommentResponse> data = comments.stream()
            .map(comment -> FeedCommentResponse.of(
                comment, profileImageUrlByUserId.get(comment.getUser().getId())))
            .toList();

        FeedComment last = hasNext ? comments.get(comments.size() - 1) : null;
        return CursorResponse.of(
            data,
            last == null ? null : last.getCreatedAt().toString(),
            last == null ? null : last.getId(),
            hasNext,
            feedCommentRepository.countByFeed_Id(pathFeedId),
            "createdAt",
            "DESCENDING"
        );
    }

    private void validateCommentRequestIds(
        UUID authenticatedUserId, UUID pathFeedId, UUID requestFeedId, UUID requestAuthorId
    ) {
        if (!authenticatedUserId.equals(requestAuthorId) || !pathFeedId.equals(requestFeedId)) {
            throw new FeedException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private OffsetDateTime parseCommentCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new FeedException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Feed getOwnedFeed(UUID userId, UUID feedId) {
        Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
            .orElseThrow(() -> new FeedException(ErrorCode.FEED_NOT_FOUND));
        if (!feed.getUser().getId().equals(userId)) {
            throw new FeedException(ErrorCode.ACCESS_DENIED);
        }
        return feed;
    }

    private FeedResponse toFeedResponse(Feed feed, boolean likedByMe) {
        List<Clothes> clothes = outfitClothesRepository.findAllByOutfit_Id(feed.getId()).stream()
            .map(OutfitClothes::getClothes)
            .toList();
        String profileImageUrl = profileRepository.findByUser_Id(feed.getUser().getId())
            .map(Profile::getProfileImageUrl)
            .map(s3StorageService::getPresignedUrl)
            .orElse(null);
        Ootd ootd = "OOTD".equalsIgnoreCase(feed.getOutfit().getCategory())
            ? ootdRepository.findById(feed.getId()).orElse(null)
            : null;

        return FeedResponse.of(
            feed,
            clothes,
            profileImageUrl,
            ootd,
            likedByMe,
            clothesItem -> s3StorageService.getPresignedUrl(clothesItem.getImageUrl())
        );
    }


    private User getCurrentUserEntity(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("DB에서 사용자를 찾을 수 없습니다."));
    }
}
