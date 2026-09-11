package com.codeit.otboo.api.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.codeit.otboo.api.weather.dto.response.WeatherGridDto;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.support.common.config.CacheConfig;
import com.codeit.otboo.support.weather.client.KakaoClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class LocationCacheTest {
    @Test
    void sameGridSkipsLookupAcrossDifferentCoordinatesAndReloadsAfterEviction() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var repository = context.getBean(WeatherRepository.class);
            var service = context.getBean(LocationService.class);
            var cache = context.getBean(CacheManager.class).getCache(CacheConfig.GRID_CACHE);
            var grid = WeatherGrid.builder().id(UUID.randomUUID()).nx(60).ny(127).build();
            when(repository.findGrid(60, 127)).thenReturn(Optional.of(grid));
            var first = service.findOrCreate(60, 127, 126.978, 37.5665);
            assertThat(service.findOrCreate(60, 127, 126.979, 37.567)).isEqualTo(first);
            verify(repository, times(1)).findGrid(60, 127);
            verifyNoInteractions(context.getBean(KakaoClient.class));
            assertThat(cache.get("60:127", WeatherGridDto.class)).isEqualTo(first);
            cache.evict("60:127");
            service.findOrCreate(60, 127, 126.978, 37.5665);
            verify(repository, times(2)).findGrid(60, 127);
            var other = WeatherGrid.builder().id(UUID.randomUUID()).nx(61).ny(127).build();
            when(repository.findGrid(61, 127)).thenReturn(Optional.of(other));
            assertThat(service.findOrCreate(61, 127, 127, 37)).isNotEqualTo(first);
        }
    }

    @Test
    void gridHasIndependentOneHourTtlAndSnapshotSurvivesRedisSerialization() {
        var manager = (RedisCacheManager) new CacheConfig().cacheManager(
                mock(RedisConnectionFactory.class), new ObjectMapper().findAndRegisterModules(),
                Duration.ofHours(3));
        manager.afterPropertiesSet();
        var gridConfig = manager.getCacheConfigurations().get(CacheConfig.GRID_CACHE);
        assertThat(gridConfig.getTtlFunction().getTimeToLive("60:127", null)).isEqualTo(Duration.ofHours(1));
        assertThat(manager.getCacheConfigurations().get(CacheConfig.WEATHER_CACHE)
                .getTtlFunction().getTimeToLive("key", null)).isEqualTo(Duration.ofHours(3));
        var value = new WeatherGridDto(UUID.randomUUID(), 60, 127, List.of("서울", "중구", "명동", ""));
        var serializer = gridConfig.getValueSerializationPair();
        assertThat(serializer.read(serializer.write(value))).isEqualTo(value);
        assertThat(gridConfig.getKeyPrefixFor(CacheConfig.GRID_CACHE)).isEqualTo("otboo:grid::");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfig {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(CacheConfig.GRID_CACHE); }
        @Bean WeatherRepository repository() { return mock(WeatherRepository.class); }
        @Bean KakaoClient kakaoClient() { return mock(KakaoClient.class); }
        @Bean LocationService locationService(WeatherRepository repository, KakaoClient client) {
            return new LocationService(repository, client);
        }
    }
}
