package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.api.dm.dto.DmRoomListResponse;
import com.codeit.otboo.domain.dm.exception.DmException;
import com.codeit.otboo.domain.dm.repository.DmRoomListProjection;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import com.codeit.otboo.domain.profile.repository.ProfileImageProjection;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DmRoomListService {

    private static final int DM_ROOM_PAGE_SIZE = 10;

    private final DmRoomRepository dmRoomRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public DmRoomListResponse getDmRooms(UUID currentUserId, String cursor) {
        CursorPosition position = decodeCursor(cursor);
        List<DmRoomListProjection> results = dmRoomRepository.findDmRoomsByUserId(
            currentUserId,
            position.sentAt(),
            position.roomId(),
            PageRequest.of(0, DM_ROOM_PAGE_SIZE + 1)
        );

        boolean hasNext = results.size() > DM_ROOM_PAGE_SIZE;
        List<DmRoomListProjection> page = hasNext ? results.subList(0, DM_ROOM_PAGE_SIZE) : results;
        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
        Map<UUID, String> profileImageUrls = findProfileImageUrls(page);

        return DmRoomListResponse.from(page, profileImageUrls, nextCursor, hasNext);
    }

    private Map<UUID, String> findProfileImageUrls(List<DmRoomListProjection> rooms) {
        if (rooms.isEmpty()) {
            return Map.of();
        }

        List<UUID> opponentIds = rooms.stream().map(DmRoomListProjection::opponentId).toList();
        return profileRepository.findProfileImagesByUserIds(opponentIds).stream()
            .collect(Collectors.toMap(ProfileImageProjection::userId,
                ProfileImageProjection::profileImageUrl, (first, ignored) -> first));
    }

    private String encodeCursor(DmRoomListProjection projection) {
        String value = projection.lastMessageAt() + "|" + projection.roomId();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private CursorPosition decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorPosition(null, null);
        }

        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);

            if (parts.length != 2) {
                throw DmException.invalidCursor();
            }

            return new CursorPosition(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));

        } catch (IllegalArgumentException e) {
            throw DmException.invalidCursor();
        }
    }

    private record CursorPosition(OffsetDateTime sentAt, UUID roomId) {
    }
}
