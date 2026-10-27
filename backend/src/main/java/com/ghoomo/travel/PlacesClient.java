package com.ghoomo.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghoomo.config.AppProperties;
import com.ghoomo.travel.TravelModels.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.*;

/** Points of interest near a coordinate from OpenStreetMap via the Overpass API (free, no key). */
@Component
public class PlacesClient {

    private static final Logger log = LoggerFactory.getLogger(PlacesClient.class);
    private static final int MAX_PLACES = 45;

    private final RestClient rest;
    private final ObjectMapper mapper;

    public PlacesClient(AppProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.rest = builder.clone()
                .baseUrl(props.travel().overpassUrl())
                .defaultHeader("User-Agent", props.travel().userAgent())
                .defaultHeader("Accept", "application/json")
                .requestFactory(GeocodingClient.TravelHttp.factory())
                .build();
        this.mapper = mapper;
    }

    /**
     * Named attractions, nature spots and restaurants within the radius.
     * Returns an empty list on failure: the planner then lets the LLM rely on its own knowledge.
     */
    @Cacheable(cacheNames = "places", key = "T(java.lang.Math).round(#lat * 100) + ':' + T(java.lang.Math).round(#lng * 100) + ':' + #radiusKm", unless = "#result.isEmpty()")
    public List<Place> nearby(double lat, double lng, int radiusKm) {
        int r = radiusKm * 1000;
        int food = Math.min(r, 6000);
        String query = "[out:json][timeout:20];("
                + "nwr[\"tourism\"~\"attraction|museum|viewpoint|gallery|zoo|theme_park\"][\"name\"]" + around(r, lat, lng) + ";"
                + "nwr[\"historic\"~\"monument|fort|castle|memorial|ruins|palace\"][\"name\"]" + around(r, lat, lng) + ";"
                + "nwr[\"natural\"~\"beach|peak|waterfall\"][\"name\"]" + around(r, lat, lng) + ";"
                + "nwr[\"leisure\"~\"park|garden|nature_reserve\"][\"name\"]" + around(Math.min(r, 8000), lat, lng) + ";"
                + "nwr[\"amenity\"=\"marketplace\"][\"name\"]" + around(Math.min(r, 8000), lat, lng) + ";"
                + "nwr[\"amenity\"~\"restaurant|cafe\"][\"name\"][\"cuisine\"]" + around(food, lat, lng) + ";"
                + ");out center 150;";
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("data", query);
            // Overpass sometimes labels JSON as application/octet-stream, so read text and parse it ourselves.
            String body = rest.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (body == null || !body.trim().startsWith("{")) {
                log.warn("Overpass returned a non-JSON reply near {},{}: {}", lat, lng,
                        body == null ? "empty" : body.substring(0, Math.min(200, body.length())));
                return List.of();
            }
            return parse(mapper.readTree(body));
        } catch (Exception e) {
            log.warn("Overpass lookup failed near {},{}: {}", lat, lng, e.getMessage());
            return List.of();
        }
    }

    private static String around(int meters, double lat, double lng) {
        return String.format(Locale.ROOT, "(around:%d,%.5f,%.5f)", meters, lat, lng);
    }

    static List<Place> parse(JsonNode root) {
        if (root == null) return List.of();
        Map<String, Place> unique = new LinkedHashMap<>();
        Map<String, Integer> perCategory = new HashMap<>();
        for (JsonNode el : root.path("elements")) {
            JsonNode tags = el.path("tags");
            String name = tags.path("name:en").asText(tags.path("name").asText(""));
            if (name.isBlank() || name.length() > 80) continue;
            double lat = el.has("lat") ? el.path("lat").asDouble() : el.path("center").path("lat").asDouble(Double.NaN);
            double lng = el.has("lon") ? el.path("lon").asDouble() : el.path("center").path("lon").asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lng)) continue;
            String category = categorize(tags);
            // Keep the mix balanced so restaurants don't crowd out sights.
            int cap = category.equals("food") ? 14 : 12;
            if (perCategory.getOrDefault(category, 0) >= cap) continue;
            if (unique.putIfAbsent(name.toLowerCase(Locale.ROOT), new Place(name, category, lat, lng)) == null) {
                perCategory.merge(category, 1, Integer::sum);
            }
            if (unique.size() >= MAX_PLACES) break;
        }
        return List.copyOf(unique.values());
    }

    static String categorize(JsonNode tags) {
        String amenity = tags.path("amenity").asText("");
        if (amenity.equals("restaurant") || amenity.equals("cafe")) return "food";
        if (amenity.equals("marketplace")) return "shopping";
        if (tags.has("natural") || tags.has("leisure")) return "nature";
        String tourism = tags.path("tourism").asText("");
        if (tags.has("historic") || tourism.equals("museum") || tourism.equals("gallery")) return "culture";
        if (tourism.equals("theme_park") || tourism.equals("zoo")) return "adventure";
        return "sight";
    }
}
