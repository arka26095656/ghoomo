package com.ghoomo.itinerary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The structured itinerary the LLM must return (and the editor sends back).
 * Stored as JSON in itineraries.plan_json.
 */
public record ItineraryPlan(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String destination,
        @Size(max = 2000) String summary,
        Double centerLat,
        Double centerLng,
        @NotEmpty @Size(max = 21) List<@Valid DayPlan> days,
        Integer estimatedTotalInr,
        @Size(max = 12) List<@Size(max = 300) String> tips,
        List<DayWeather> weather) {

    public record DayPlan(
            @Min(1) int day,
            @Size(max = 200) String theme,
            @Size(max = 12) List<@Valid Activity> activities) {}

    public record Activity(
            @Size(max = 20) String time,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 600) String description,
            @Size(max = 40) String category,
            Double lat,
            Double lng,
            @Min(0) Integer estCostInr) {}

    /** Added by the backend from Open-Meteo, never by the LLM. */
    public record DayWeather(String date, Double maxC, Double minC, Integer rainChance, Integer code) {}

    public ItineraryPlan withWeather(List<DayWeather> w) {
        return new ItineraryPlan(title, destination, summary, centerLat, centerLng, days, estimatedTotalInr, tips, w);
    }

    /** Recomputes the total from activity costs so the number in the header always matches the cards. */
    public ItineraryPlan withRecomputedTotal() {
        int total = 0;
        for (DayPlan d : safe(days)) {
            for (Activity a : safe(d.activities())) {
                if (a.estCostInr() != null) total += a.estCostInr();
            }
        }
        Integer value = total > 0 ? Integer.valueOf(total) : estimatedTotalInr;
        return new ItineraryPlan(title, destination, summary, centerLat, centerLng, days, value, tips, weather);
    }

    static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
