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
            return findGrid(nx, ny).orElseThrow(() -> exception);
        }
    }
}
