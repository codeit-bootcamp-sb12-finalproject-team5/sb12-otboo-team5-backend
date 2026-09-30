package com.codeit.otboo.api.auth.service;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;
import java.util.UUID;

@Getter
public class CustomOidcUser extends DefaultOidcUser {
    private final UUID userId;

    public CustomOidcUser(
            UUID userId,
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            OidcUserInfo userInfo
    ) {
        super(authorities, idToken, userInfo);
        this.userId = userId;
    }
}
