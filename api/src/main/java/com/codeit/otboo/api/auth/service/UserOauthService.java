package com.codeit.otboo.api.auth.service;

import com.codeit.otboo.api.auth.dto.UserOauthInfo;
import com.codeit.otboo.api.profile.service.ProfileService;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserOauthLink;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.entity.OAuthProvider;
import com.codeit.otboo.domain.user.repository.UserOauthLinkRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserOauthService {

    private final UserRepository userRepository;
    private final UserOauthLinkRepository userOauthLinkRepository;
    private final ProfileService profileService;

    @Transactional
    public User loginOrSignup(UserOauthInfo oauthInfo) {
        return userOauthLinkRepository.findByProviderAndProviderId(oauthInfo.provider(), oauthInfo.providerId())
                .map(UserOauthLink::getUser)
                .orElseGet(() -> signup(oauthInfo));
    }
    private User signup(UserOauthInfo oauthInfo) {
        User user = User.builder()
                .email(resolveEmail(oauthInfo))
                .name(resolveName(oauthInfo))
                .role(UserRole.USER)
                .locked(false)
                .tokenVersion(0)
                .build();
        User savedUser = userRepository.save(user);

        UserOauthLink oauthLink = UserOauthLink.builder()
                .provider(oauthInfo.provider())
                .providerId(oauthInfo.providerId())
                .user(savedUser)
                .build();
        userOauthLinkRepository.save(oauthLink);
        profileService.createProfile(savedUser);
        return savedUser;
    }

    private String resolveEmail(UserOauthInfo oauthInfo) {
        String email = oauthInfo.email();
        if (email != null && !email.isBlank()) {
            return email;
        }

        if (oauthInfo.provider() == OAuthProvider.KAKAO) {
            return createKakaoEmail(oauthInfo.nickname());
        }

        throw new IllegalArgumentException("Google OAuth user did not provide an email address.");
    }

    private String createKakaoEmail(String nickname) {
        String localPart = nickname == null ? "" : nickname
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        if (localPart.isBlank()) {
            localPart = "kakao-user";
        }

        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int maxLocalPartLength = 64 - randomSuffix.length() - 1;
        if (localPart.length() > maxLocalPartLength) {
            localPart = localPart.substring(0, maxLocalPartLength);
        }

        return localPart + "-" + randomSuffix + "@kakao.com";
    }

    private String resolveName(UserOauthInfo oauthInfo) {
        String nickname = oauthInfo.nickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname.length() <= 50 ? nickname : nickname.substring(0, 50);
        }
        return oauthInfo.provider().name() + " 사용자";
    }

}
