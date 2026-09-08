package com.codeit.otboo.api.common.init;

import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버 시작 시 어드민 계정을 초기화합니다.
 *
 * 이미 존재하면 아무 작업도 하지 않습니다 (멱등).
 * 다중 인스턴스 환경에서 동시 부팅 시 중복 생성될 수 있으므로,
 * users.email 의 UNIQUE 제약을 최종 방어선으로 둡니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminProperties.email())) {
            log.info("어드민 계정이 이미 존재합니다: {}", adminProperties.email());
            return;
        }

        try {
            User admin = User.builder()
                    .email(adminProperties.email())
                    .name(adminProperties.name())
                    .password(passwordEncoder.encode(adminProperties.password()))
                    .role(UserRole.ADMIN)
                    .locked(false)
                    .tokenVersion(0)
                    .build();

            userRepository.save(admin);
            log.info("어드민 계정을 생성했습니다: {}", adminProperties.email());

        } catch (Exception e) {
            // 다중 인스턴스 동시 부팅 시 UNIQUE 제약으로 실패할 수 있습니다.
            log.warn("어드민 계정 생성 실패 (이미 생성되었을 수 있음): {}", e.getMessage());
        }
    }
}
