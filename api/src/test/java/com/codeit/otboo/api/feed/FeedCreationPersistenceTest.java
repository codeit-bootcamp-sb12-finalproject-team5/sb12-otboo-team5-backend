package com.codeit.otboo.api.feed;

import com.codeit.otboo.api.feed.dto.FeedRequest;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.feed.repository.FeedCommentRepository;
import com.codeit.otboo.domain.feed.repository.FeedLikeRepository;
import com.codeit.otboo.domain.feed.repository.FeedRepository;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.repository.OotdRepository;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedCreationPersistenceTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsFeedThroughRealJpaSaveAndReloadsItsOutfit(boolean hasImage) {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Outfit.class)
                .addAnnotatedClass(Feed.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:feed_" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            OffsetDateTime now = OffsetDateTime.now();
            User user = User.builder().id(UUID.randomUUID()).email("test@example.com").name("author")
                    .role(UserRole.USER).locked(false).tokenVersion(0).createdAt(now).updatedAt(now).build();
            Outfit outfit = Outfit.builder().id(UUID.randomUUID()).user(user).name("outfit")
                    .category("OUTFIT").imageKey(hasImage ? "outfits/generated.png" : null)
                    .createdAt(now).updatedAt(now).build();
            session.persist(user);
            session.persist(outfit);
            session.flush();

            var jpaRepository = new SimpleJpaRepository<Feed, UUID>(Feed.class, session);
            FeedRepository feeds = mock(FeedRepository.class);
            when(feeds.existsById(outfit.getId())).thenAnswer(call -> jpaRepository.existsById(outfit.getId()));
            when(feeds.save(any(Feed.class))).thenAnswer(call -> {
                Feed feed = call.getArgument(0);
                // Supply audit fields because this isolated Hibernate setup has no Spring auditing context.
                ReflectionTestUtils.setField(feed, "createdAt", now);
                ReflectionTestUtils.setField(feed, "updatedAt", now);
                return jpaRepository.save(feed);
            });
            OutfitRepository outfits = mock(OutfitRepository.class);
            when(outfits.findByIdAndDeletedAtIsNull(outfit.getId())).thenReturn(Optional.of(outfit));
            S3StorageService storage = mock(S3StorageService.class);
            when(storage.getPresignedUrl(any())).thenAnswer(call ->
                    call.getArgument(0) == null ? null : "https://example.com/" + call.getArgument(0));
            FeedService service = new FeedService(mock(UserRepository.class), feeds, outfits,
                    mock(OotdRepository.class), mock(OutfitClothesRepository.class),
                    mock(ProfileRepository.class), mock(FeedLikeRepository.class),
                    mock(FeedCommentRepository.class), storage, mock(ApplicationEventPublisher.class));

            var response = service.create(user.getId(), new FeedRequest(user.getId(), outfit.getId(), "content"));
            session.flush();
            transaction.commit();
            session.clear();

            Feed reloaded = session.find(Feed.class, outfit.getId());
            assertThat(reloaded.getOutfit().getId()).isEqualTo(outfit.getId());
            assertThat(reloaded.getContent()).isEqualTo("content");
            assertThat(response.id()).isEqualTo(outfit.getId());
            assertThat(response.imageUrl()).isEqualTo(hasImage ? "https://example.com/outfits/generated.png" : null);
        }
    }
}
