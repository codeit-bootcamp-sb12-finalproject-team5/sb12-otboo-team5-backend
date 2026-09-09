package com.codeit.otboo.api.admin.service;

import com.codeit.otboo.api.admin.dto.UserLockUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserRoleUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserSearchCondition;
import com.codeit.otboo.api.common.dto.CursorResponse;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.RefreshTokenRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /** 사용자 목록 조회 */
    @Transactional(readOnly = true)
    public CursorResponse<UserDto> getUsers(UserSearchCondition condition) {
        String sortBy = condition.sortByOrDefault();
        Sort.Direction direction = "ASCENDING".equals(condition.sortDirectionOrDefault())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(
                0, condition.limitOrDefault(), Sort.by(direction, sortBy));

        Specification<User> spec = UserSpecification.from(condition);
        Page<User> page = userRepository.findAll(spec, pageRequest);

        List<UserDto> data = page.getContent().stream()
                .map(UserDto::from)
                .toList();

        if (data.isEmpty() || !page.hasNext()) {
            return CursorResponse.last(data, page.getTotalElements(),
                    sortBy, condition.sortDirectionOrDefault());
        }

        UserDto last = data.get(data.size() - 1);
        return CursorResponse.of(data, last.createdAt().toString(), last.id(),
                true, page.getTotalElements(), sortBy, condition.sortDirectionOrDefault());
    }

    /** 권한 변경 - 대상 사용자는 즉시 로그아웃됩니다 */
    @Transactional
    public UserDto updateRole(UUID userId, UserRoleUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserException::notFound);

        user.setRole(request.role());
        invalidateSessions(user);

        log.info("권한 변경: {} -> {}", user.getEmail(), request.role());
        return UserDto.from(user);
    }

    /** 계정 잠금 상태 변경 - 잠글 경우 즉시 로그아웃됩니다 */
    @Transactional
    public UserDto updateLock(UUID userId, UserLockUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserException::notFound);

        user.setLocked(request.locked());

        if (Boolean.TRUE.equals(request.locked())) {
            invalidateSessions(user);
        }

        log.info("계정 잠금 변경: {} -> {}", user.getEmail(), request.locked());
        return UserDto.from(user);
    }

    /**
     * 기존 세션 무효화.
     * tokenVersion 을 증가시키면 이미 발급된 Access Token 이 즉시 무효가 되고,
     * Refresh Token 을 삭제하면 재발급도 차단됩니다.
     */
    private void invalidateSessions(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenRepository.deleteByUser(user);
    }
}
