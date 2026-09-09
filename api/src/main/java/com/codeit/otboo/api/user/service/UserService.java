package com.codeit.otboo.api.user.service;

import com.codeit.otboo.api.user.dto.ChangePasswordRequest;
import com.codeit.otboo.api.user.dto.UserCreateRequest;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw UserException.duplicateEmail()
                    .addDetail("email", request.email());
        }

        User user = User.builder()
                .email(request.email())
                .name(request.name())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .locked(false)
                .tokenVersion(0)
                .build();

        User saved = userRepository.save(user);
        log.info("회원가입 완료: {}", saved.getEmail());

        return UserDto.from(saved);
    }

    /** 비밀번호 변경 - 임시 비밀번호는 함께 파기 */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserException::notFound);

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setTempPassword(null);
        user.setTempPasswordExpiresAt(null);

        log.info("비밀번호 변경: {}", user.getEmail());
    }
}
