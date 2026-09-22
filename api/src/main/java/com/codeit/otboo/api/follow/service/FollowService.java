package com.codeit.otboo.api.follow.service;

import com.codeit.otboo.api.follow.dto.response.FollowUserDto;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.follow.entity.Follow;
import com.codeit.otboo.domain.follow.repository.FollowRepository;
import com.codeit.otboo.domain.profile.repository.ProfileImageProjection;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FollowService {

  private final FollowRepository followRepository;
  private final ProfileRepository profileRepository;

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

}
