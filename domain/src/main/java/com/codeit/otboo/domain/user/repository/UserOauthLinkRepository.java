package com.codeit.otboo.domain.user.repository;

import com.codeit.otboo.domain.user.entity.OAuthProvider;
import com.codeit.otboo.domain.user.entity.UserOauthLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOauthLinkRepository extends JpaRepository<UserOauthLink, UUID> {

    Optional<UserOauthLink> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    boolean existsByProviderAndProviderId(OAuthProvider provider, String providerId);

}
