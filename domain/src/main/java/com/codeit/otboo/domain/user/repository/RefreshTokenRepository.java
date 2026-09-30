package com.codeit.otboo.domain.user.repository;

import com.codeit.otboo.domain.user.entity.RefreshToken;
import com.codeit.otboo.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);
}
