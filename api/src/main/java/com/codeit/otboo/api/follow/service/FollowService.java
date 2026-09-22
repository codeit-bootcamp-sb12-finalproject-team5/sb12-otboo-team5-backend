package com.codeit.otboo.api.follow.service;

import com.codeit.otboo.api.follow.dto.request.FollowCreateRequest;
import com.codeit.otboo.api.notification.event.NotificationEvents;
import com.codeit.otboo.api.follow.dto.response.FollowDto;
import com.codeit.otboo.api.follow.dto.response.FollowSummaryDto;
import com.codeit.otboo.api.follow.dto.response.FollowUserDto;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.follow.entity.Follow;
import com.codeit.otboo.domain.follow.exception.FollowException;
import com.codeit.otboo.domain.follow.repository.FollowRepository;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileImageProjection;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

  private final FollowRepository followRepository;
  private final ProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public FollowDto createFollow(FollowCreateRequest request) {

    User follower = userRepository.findById(request.followerId())
        .orElseThrow(UserException::notFound);

    User followee = userRepository.findById(request.followeeId())
        .orElseThrow(UserException::notFound);

    if(followRepository.existsByFollowerAndFollowee(follower, followee)) {
      throw FollowException.duplicate();
    }

    Follow follow = Follow.create(follower, followee);

    Follow savedFollow = followRepository.save(follow);

    eventPublisher.publishEvent(NotificationEvents.followCreated(savedFollow.getId()));

    List<UUID> userIds = List.of(
        savedFollow.getFollower().getId(),
        savedFollow.getFollowee().getId()
    );

    List<ProfileImageProjection> profileImages =
        profileRepository.findProfileImagesByUserIds(userIds);

    String followerProfileImageUrl = profileImages.stream()
        .filter(image ->
            image.userId().equals(savedFollow.getFollower().getId())
        )
        .map(ProfileImageProjection::profileImageUrl)
        .findFirst()
        .orElse(null);

    String followeeProfileImageUrl = profileImages.stream()
        .filter(image ->
            image.userId().equals(savedFollow.getFollowee().getId())
        )
        .map(ProfileImageProjection::profileImageUrl)
        .findFirst()
        .orElse(null);

    FollowUserDto followerDto = new FollowUserDto(
        savedFollow.getFollower().getId(),
        savedFollow.getFollower().getName(),
        followerProfileImageUrl
    );

    FollowUserDto followeeDto = new FollowUserDto(
        savedFollow.getFollowee().getId(),
        savedFollow.getFollowee().getName(),
        followeeProfileImageUrl
    );

    return new FollowDto(
        savedFollow.getId(),
        followeeDto,
        followerDto
    );
  }

  public CursorResponse<FollowUserDto> findFolloweesByFollowerId(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      Sort.Direction direction
  ) {
    List<Follow> follows = followRepository.findFolloweesByFollowerId(
        followerId,
        cursor,
        idAfter,
        limit + 1,
        direction
    );

    boolean hasNext = follows.size() > limit;

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      follows = follows.subList(0, limit);

      Follow lastFollow = follows.get(follows.size() - 1);

      nextCursor = lastFollow.getCreatedAt().toString();
      nextIdAfter = lastFollow.getId();
    }

    long totalCount = followRepository.countFolloweesByFollowerId(followerId);
    String sortBy = "createdAt";
    String sortDirection = direction.name();

    List<UUID> followeeIds = follows.stream()
        .map(follow -> follow.getFollowee().getId())
        .toList();

    List<ProfileImageProjection> profileImages =
        profileRepository.findProfileImagesByUserIds(followeeIds);

    List<FollowUserDto> data = follows.stream()
        .map(follow -> {
          Optional<ProfileImageProjection> imageProjection = profileImages.stream()
              .filter(image ->
                  image.userId().equals(follow.getFollowee().getId())
              )
              .findFirst();

          String profileImageUrl = imageProjection
              .map(image -> image.profileImageUrl())
              .orElse(null);

          FollowUserDto followUserDto = new FollowUserDto(
              follow.getFollowee().getId(),
              follow.getFollowee().getName(),
              profileImageUrl
          );

          return followUserDto;
        })
        .toList();

    return CursorResponse.of(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }

  public CursorResponse<FollowUserDto> findFollowersByFolloweeId(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      Sort.Direction direction
  ) {
    List<Follow> follows = followRepository.findFollowersByFolloweeId(
        followeeId,
        cursor,
        idAfter,
        limit + 1,
        direction
    );

    boolean hasNext = follows.size() > limit;

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      follows = follows.subList(0, limit);

      Follow lastFollow = follows.get(follows.size() - 1);

      nextCursor = lastFollow.getCreatedAt().toString();
      nextIdAfter = lastFollow.getId();
    }

    long totalCount = followRepository.countFollowersByFolloweeId(followeeId);
    String sortBy = "createdAt";
    String sortDirection = direction.name();

    List<UUID> followerIds = follows.stream()
        .map(follow -> follow.getFollower().getId())
        .toList();

    List<ProfileImageProjection> profileImages =
        profileRepository.findProfileImagesByUserIds(followerIds);

    List<FollowUserDto> data = follows.stream()
        .map(follow -> {
          Optional<ProfileImageProjection> imageProjection = profileImages.stream()
              .filter(image ->
                  image.userId().equals(follow.getFollower().getId())
              )
              .findFirst();

          String profileImageUrl = imageProjection
              .map(image -> image.profileImageUrl())
              .orElse(null);

          FollowUserDto followUserDto = new FollowUserDto(
              follow.getFollower().getId(),
              follow.getFollower().getName(),
              profileImageUrl
          );

          return followUserDto;
        })
        .toList();

    return CursorResponse.of(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }

  public FollowSummaryDto findFollowSummary(
      UUID userId,
      UUID currentUserId
  ) {
    long followerCount =
        followRepository.countFollowersByFolloweeId(userId);

    long followingCount =
        followRepository.countFolloweesByFollowerId(userId);

    User currentUser = userRepository.findById(currentUserId)
        .orElseThrow(UserException::notFound);

    User targetUser = userRepository.findById(userId)
        .orElseThrow(UserException::notFound);

    boolean followedByMe =
        followRepository.existsByFollowerAndFollowee(currentUser, targetUser);

    UUID followedByMeId = followRepository.findByFollowerAndFollowee(currentUser, targetUser)
        .map(Follow::getId)
        .orElse(null);

    boolean followingMe =
        followRepository.existsByFollowerAndFollowee(targetUser, currentUser);

    return new FollowSummaryDto(
        userId,
        followerCount,
        followingCount,
        followedByMe,
        followedByMeId,
        followingMe
    );
  }

  public void deleteFollow(UUID followId, UUID currentUserId) {

    Follow follow = followRepository.findById(followId)
        .orElseThrow(FollowException::notFound);

    if(!follow.getFollower().getId().equals(currentUserId)) {
      throw ProfileException.accessDenied();
    }

    followRepository.delete(follow);
  }

}
