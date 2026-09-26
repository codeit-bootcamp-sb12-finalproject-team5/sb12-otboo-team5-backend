package com.codeit.otboo.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.api.auth.dto.UserOauthInfo;
import com.codeit.otboo.api.profile.service.ProfileService;
import com.codeit.otboo.domain.user.entity.OAuthProvider;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserOauthLink;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.repository.UserOauthLinkRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserOauthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserOauthLinkRepository userOauthLinkRepository = mock(UserOauthLinkRepository.class);
    private final ProfileService profileService = mock(ProfileService.class);
    private final UserOauthService userOauthService = new UserOauthService(
            userRepository, userOauthLinkRepository, profileService);

    @Test
    void createsKakaoUserWithGeneratedEmailWhenProviderDoesNotSupplyOne() {
        UserOauthInfo info = new UserOauthInfo(
                OAuthProvider.KAKAO, "provider-user-id", "woody", null, null);
        when(userOauthLinkRepository.findByProviderAndProviderId(
                OAuthProvider.KAKAO, "provider-user-id")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userOauthService.loginOrSignup(info);

        assertThat(user.getEmail()).matches("woody-[0-9a-f]{12}@kakao\\.com");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getName()).isEqualTo("woody");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getLocked()).isFalse();
        assertThat(user.getTokenVersion()).isZero();
        verify(userOauthLinkRepository).save(any(UserOauthLink.class));
        verify(profileService).createProfile(user);
    }

    @Test
    void preservesEmailProvidedByGoogle() {
        UserOauthInfo info = new UserOauthInfo(
                OAuthProvider.GOOGLE, "provider-user-id", "woody", "woody@gmail.com", null);
        when(userOauthLinkRepository.findByProviderAndProviderId(
                OAuthProvider.GOOGLE, "provider-user-id")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userOauthService.loginOrSignup(info);

        assertThat(user.getEmail()).isEqualTo("woody@gmail.com");
    }

    @Test
    void usesKakaoNicknameInGeneratedEmail() {
        UserOauthInfo info = new UserOauthInfo(
                OAuthProvider.KAKAO, "provider-user-id-2", "우디", null, null);
        when(userOauthLinkRepository.findByProviderAndProviderId(
                OAuthProvider.KAKAO, "provider-user-id-2")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userOauthService.loginOrSignup(info);

        assertThat(user.getEmail()).matches("우디-[0-9a-f]{12}@kakao\\.com");
    }

    @Test
    void reusesLinkedOauthUserWithoutCreatingAnotherProfile() {
        User linkedUser = User.builder()
                .email("linked-kakao@kakao.com")
                .name("카카오 사용자")
                .role(UserRole.USER)
                .locked(false)
                .tokenVersion(0)
                .build();
        UserOauthLink link = UserOauthLink.builder()
                .provider(OAuthProvider.KAKAO)
                .providerId("provider-user-id")
                .user(linkedUser)
                .build();
        when(userOauthLinkRepository.findByProviderAndProviderId(
                OAuthProvider.KAKAO, "provider-user-id")).thenReturn(Optional.of(link));

        User user = userOauthService.loginOrSignup(new UserOauthInfo(
                OAuthProvider.KAKAO, "provider-user-id", "카카오 사용자", null, null));

        assertThat(user).isSameAs(linkedUser);
        verify(profileService, org.mockito.Mockito.never()).createProfile(any(User.class));
    }
}
