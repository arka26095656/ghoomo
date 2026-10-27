package com.ghoomo.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghoomo.config.AppProperties;
import com.ghoomo.travel.TravelModels.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Place name → coordinates using OpenStreetMap Nominatim (free, needs a descriptive User-Agent, max ~1 req/s). */
@Component
public class GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(GeocodingClient.class);

    private final RestClient rest;

    public GeocodingClient(AppProperties props, RestClient.Builder builder) {
        this.rest = builder.clone()
                .baseUrl(props.travel().nominatimUrl())
                .defaultHeader("User-Agent", props.travel().userAgent())
                .requestFactory(TravelHttp.factory())
                .build();
    }

    @Cacheable(cacheNames = "geocode", key = "#query.toLowerCase()", unless = "#result == null")
    public Optional<GeoPoint> geocode(String query) {
        try {
            JsonNode arr = rest.get()
                    .uri(u -> u.path("/search").queryParam("q", query).queryParam("format", "json")
                            .queryParam("limit", 1).queryParam("addressdetails", 0).build())
                    .retrieve()
                    .body(JsonNode.class);
            if (arr != null && arr.isArray() && !arr.isEmpty()) {
                JsonNode hit = arr.get(0);
                String display = hit.path("display_name").asText(query);
                String name = hit.path("name").asText(display.split(",")[0]);
                return Optional.of(new GeoPoint(name, display, hit.path("lat").asDouble(), hit.path("lon").asDouble()));
            }
        } catch (Exception e) {
            log.warn("Nominatim lookup failed for '{}': {}", query, e.getMessage());
        }
        return Gazetteer.lookup(query);
    }

    static final class TravelHttp {
        static SimpleClientHttpRequestFactory factory() {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(5_000);
            f.setReadTimeout(20_000);
            return f;
        }
    }
}
