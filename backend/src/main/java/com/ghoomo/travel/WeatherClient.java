package com.ghoomo.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghoomo.config.AppProperties;
import com.ghoomo.itinerary.ItineraryPlan.DayWeather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Daily forecast from Open-Meteo (free, no key). Forecasts only reach ~16 days ahead. */
@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    public static final int MAX_FORECAST_DAYS = 16;

    private final RestClient rest;

    public WeatherClient(AppProperties props, RestClient.Builder builder) {
        this.rest = builder.clone()
                .baseUrl(props.travel().openMeteoUrl())
                .requestFactory(GeocodingClient.TravelHttp.factory())
                .build();
    }

    /** Empty when the dates are outside the forecast window or the call fails. */
    @Cacheable(cacheNames = "weather", key = "T(java.lang.Math).round(#lat * 100) + ':' + T(java.lang.Math).round(#lng * 100) + ':' + #from + ':' + #days")
    public List<DayWeather> forecast(double lat, double lng, LocalDate from, int days) {
        LocalDate today = LocalDate.now();
        LocalDate start = from == null ? today : from;
        LocalDate end = start.plusDays(Math.max(1, days) - 1L);
        LocalDate limit = today.plusDays(MAX_FORECAST_DAYS - 1L);
        if (start.isBefore(today) || start.isAfter(limit)) return List.of();
        if (end.isAfter(limit)) end = limit;
        final LocalDate s = start, e = end;
        try {
            JsonNode daily = rest.get()
                    .uri(u -> u.path("/forecast")
                            .queryParam("latitude", lat).queryParam("longitude", lng)
                            .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code")
                            .queryParam("timezone", "auto")
                            .queryParam("start_date", s).queryParam("end_date", e)
                            .build())
                    .retrieve()
                    .body(JsonNode.class)
                    .path("daily");
            List<DayWeather> out = new ArrayList<>();
            JsonNode dates = daily.path("time");
            for (int i = 0; i < dates.size(); i++) {
                out.add(new DayWeather(dates.get(i).asText(),
                        num(daily.path("temperature_2m_max").get(i)),
                        num(daily.path("temperature_2m_min").get(i)),
                        intOrNull(daily.path("precipitation_probability_max").get(i)),
                        intOrNull(daily.path("weather_code").get(i))));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Open-Meteo failed for {},{}: {}", lat, lng, ex.getMessage());
            return List.of();
        }
    }

    private static Double num(JsonNode n) {
        return n == null || n.isNull() ? null : n.asDouble();
    }

    private static Integer intOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asInt();
    }
}
