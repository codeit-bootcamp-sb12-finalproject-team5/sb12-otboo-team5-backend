package com.codeit.otboo.support.weather.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KmaClientTest {
    private MockRestServiceServer server;
    private KmaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KmaClient("test-key", builder.build(), new ObjectMapper());
    }

    @Test
    void parsesObservationAndNormalizesRequestTime() {
        server.expect(queryParam("base_time", "1400"))
            .andExpect(queryParam("base_date", "20260908"))
            .andExpect(queryParam("nx", "60"))
            .andExpect(queryParam("ny", "127"))
            .andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                {"baseDate":"20260908","baseTime":"1400","category":"T1H","obsrValue":"25.3"}
                ]}}}}
                """, MediaType.APPLICATION_JSON));

        var result = client.findObservation(LocalDateTime.of(2026, 9, 8, 14, 35), 60, 127);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().observedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 14, 0));
        assertThat(result.orElseThrow().categories()).containsEntry("T1H", "25.3");
        server.verify();
    }

    @Test
    void parsesForecastPoints() {
        server.expect(queryParam("base_time", "1100"))
            .andRespond(withSuccess("""
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                {"fcstDate":"20260908","fcstTime":"1500","category":"TMP","fcstValue":"26"}
                ]}}}}
                """, MediaType.APPLICATION_JSON));

        var result = client.findVillageForecast(LocalDateTime.of(2026, 9, 8, 11, 0), 60, 127);

        assertThat(result).isPresent();
        var point = result.orElseThrow().points().get(0);
        assertThat(point.forecastAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 15, 0));
        assertThat(point.category()).isEqualTo("TMP");
        assertThat(point.value()).isEqualTo("26");
        server.verify();
    }

    @Test
    void latestObservationChecksThreeDistinctHourlyBases() {
        java.util.List<String> bases = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(request -> bases.add(request.getURI().getQuery()))
                .andRespond(withSuccess("""
                    {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}
                    """, MediaType.APPLICATION_JSON));
        }

        assertThat(client.findLatestObservation(60, 127)).isEmpty();
        assertThat(bases).hasSize(3).doesNotHaveDuplicates();
        server.verify();
    }

    @Test
    void malformedResponseReturnsEmpty() {
        server.expect(queryParam("base_time", "1100"))
            .andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));

        assertThat(client.findVillageForecast(LocalDateTime.of(2026, 9, 8, 11, 0), 60, 127)).isEmpty();
        server.verify();
    }

    @Test
    void blankKeySkipsRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(builder).build();
        KmaClient noKeyClient = new KmaClient("", builder.build(), new ObjectMapper());

        assertThat(noKeyClient.findLatestObservation(60, 127)).isEmpty();
        assertThat(noKeyClient.findLatestVillageForecast(60, 127)).isEmpty();
        emptyServer.verify();
    }
}
