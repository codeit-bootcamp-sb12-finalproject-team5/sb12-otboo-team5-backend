package com.codeit.otboo.domain.profile.repository;

import com.codeit.otboo.domain.profile.entity.Profile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @Query("""
            select profile.user.id as userId,
                profile.profileImageUrl as profileImageUrl
            from Profile profile
            where profile.user.id in :userIds
            """)
    List<ProfileImageRow> findProfileImageRowsByUserIds(@Param("userIds") Collection<UUID> userIds);

    default List<ProfileImageProjection> findProfileImagesByUserIds(Collection<UUID> userIds) {
        return findProfileImageRowsByUserIds(userIds).stream()
                .map(ProfileImageProjection::from)
                .toList();
    }
}
