package com.codeit.otboo.api.weather.repository;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WeatherRepository {
    private final WeatherGridRepository gridRepository;
    private final WeatherObservationRepository observationRepository;
    private final WeatherForecastRepository forecastRepository;

    public Optional<WeatherGrid> findGrid(int nx, int ny) {
        return gridRepository.findByNxAndNy(nx, ny);
    }

    public WeatherGrid findOrCreateGrid(int nx, int ny, List<String> names) {
        Optional<WeatherGrid> existing = findGrid(nx, ny);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return gridRepository.saveAndFlush(WeatherGrid.create(nx, ny, names));
        } catch (DataIntegrityViolationException exception) {
            // JPA 저장 트랜잭션 종료 후 동시에 생성된 격자를 다시 조회한다.
            // 다른 무결성 오류라면 원래 예외를 유지한다.
            return findGrid(nx, ny).orElseThrow(() -> exception);
        }
    }
}
