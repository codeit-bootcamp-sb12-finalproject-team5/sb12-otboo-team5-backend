package com.codeit.otboo.api.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class WeatherRepositoryTest {
    private final WeatherGridRepository grids = mock(WeatherGridRepository.class);
    private final WeatherRepository repository = new WeatherRepository(grids,
            mock(WeatherObservationRepository.class), mock(WeatherForecastRepository.class));

    @Test
    void createsEnabledGridWithRegionNames() {
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.empty());
        when(grids.saveAndFlush(any(WeatherGrid.class))).thenAnswer(call -> call.getArgument(0));

        WeatherGrid result = repository.findOrCreateGrid(60, 127, List.of("서울특별시", "중구"));

        assertThat(result.getNx()).isEqualTo(60);
        assertThat(result.getNy()).isEqualTo(127);
        assertThat(result.getLocationNames()).containsExactly("서울특별시", "중구", "", "");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getLastRequestedAt()).isNotNull();
    }

    @Test
    void preservesExistingRegionNames() {
        WeatherGrid grid = WeatherGrid.create(60, 127, List.of("서울특별시", "중구"));
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.of(grid));

        assertThat(repository.findOrCreateGrid(60, 127, List.of())).isSameAs(grid);
        assertThat(grid.getLocationNames()).containsExactly("서울특별시", "중구", "", "");
        verify(grids, never()).saveAndFlush(any());
    }

    @Test
    void readsConcurrentInsertAfterFailedSave() {
        WeatherGrid winner = WeatherGrid.create(60, 127, List.of("서울특별시"));
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(grids.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(repository.findOrCreateGrid(60, 127, List.of())).isSameAs(winner);
    }

    @Test
    void propagatesOtherIntegrityFailures() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("invalid data");
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.empty());
        when(grids.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> repository.findOrCreateGrid(60, 127, List.of()))
                .isSameAs(failure);
    }
}
