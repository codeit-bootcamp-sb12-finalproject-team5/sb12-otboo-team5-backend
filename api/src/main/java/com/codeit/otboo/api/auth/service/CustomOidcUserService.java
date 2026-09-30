package com.codeit.otboo.api.auth.service;

import com.codeit.otboo.api.auth.dto.UserOauthInfo;
import com.codeit.otboo.domain.user.entity.OAuthProvider;
import com.codeit.otboo.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserOauthService userOauthService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest
                        .getClientRegistration()
                        .getRegistrationId();
        OAuthProvider provider = OAuthProvider.from(registrationId);
        UserOauthInfo oauthInfo = UserOauthInfo.of(provider, oidcUser);

        User user = userOauthService.loginOrSignup(oauthInfo);

        return new CustomOidcUser(
                user.getId(),
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );
    }

}
