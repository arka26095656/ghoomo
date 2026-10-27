package com.ghoomo.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghoomo.ai.LlmRequest;
import com.ghoomo.ai.LlmRequest.Message;
import com.ghoomo.ai.LlmRequest.Task;
import com.ghoomo.ai.LlmRouter;
import com.ghoomo.ai.MockLlmProvider;
import com.ghoomo.common.ApiException;
import com.ghoomo.itinerary.ItineraryPlan;
import com.ghoomo.itinerary.ItineraryPlan.Activity;
import com.ghoomo.itinerary.ItineraryPlan.DayPlan;
import com.ghoomo.itinerary.ItineraryPlan.DayWeather;
import com.ghoomo.itinerary.PlanJson;
import com.ghoomo.travel.GeocodingClient;
import com.ghoomo.travel.PlacesClient;
import com.ghoomo.travel.TravelModels.GeoPoint;
import com.ghoomo.travel.TravelModels.Place;
import com.ghoomo.travel.WeatherClient;
import com.ghoomo.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Consumer;

/**
 * The planning pipeline:
 * 1. LLM extracts a structured TripIntent from the conversation.
 * 2. Backend grounds it with real data: geocode → nearby places → weather.
 * 3. LLM writes a JSON itinerary from that context; backend validates and repairs it.
 */
@Service
public class TripPlanner {

    private static final Logger log = LoggerFactory.getLogger(TripPlanner.class);
    private static final double MAX_DISTANCE_KM = 150;

    public record PlanResult(ItineraryPlan plan, TripIntent intent, String provider) {}

    private final LlmRouter llm;
    private final GeocodingClient geocoding;
    private final PlacesClient places;
    private final WeatherClient weather;
    private final ObjectMapper mapper;

    public TripPlanner(LlmRouter llm, GeocodingClient geocoding, PlacesClient places,
                       WeatherClient weather, ObjectMapper mapper) {
        this.llm = llm;
        this.geocoding = geocoding;
        this.places = places;
        this.weather = weather;
        this.mapper = mapper;
    }

    public TripIntent detectIntent(List<Message> history) {
        var req = new LlmRequest(Task.INTENT, PromptTemplates.intent(LocalDate.now()), history, true, 0.0, Map.of());
        try {
            return PlanJson.parse(mapper, llm.complete(req).text(), TripIntent.class);
        } catch (IllegalArgumentException e) {
            log.warn("Intent JSON unreadable, treating as chat: {}", e.getMessage());
            return new TripIntent("chat", null, null, null, null, null, null, List.of());
        }
    }

    public PlanResult plan(TripIntent rawIntent, User user, Consumer<String> status) {
        TripIntent intent = rawIntent.normalised();
        LocalDate start = parseDate(intent.startDate());

        status.accept("Finding " + intent.destination() + " on the map");
        GeoPoint center = geocoding.geocode(intent.destination()).orElse(null);

        List<Place> nearby = List.of();
        if (center != null) {
            status.accept("Looking up places to visit around " + center.name());
            nearby = places.nearby(center.lat(), center.lng(), 20);
        }

        List<DayWeather> forecast = List.of();
        if (center != null && start != null) {
            status.accept("Checking the forecast for your dates");
            forecast = weather.forecast(center.lat(), center.lng(), start, intent.days());
        }

        status.accept("Drafting your " + intent.days() + "-day itinerary");
        String context = buildContext(intent, user, center, nearby, forecast);
        Map<String, Object> hints = new HashMap<>();
        hints.put(MockLlmProvider.HINT_INTENT, intent);
        hints.put(MockLlmProvider.HINT_PLACES, nearby);
        if (center != null) hints.put(MockLlmProvider.HINT_CENTER, center);

        List<Message> msgs = new ArrayList<>(List.of(Message.user(context)));
        var req = new LlmRequest(Task.PLAN, PromptTemplates.plan(LocalDate.now()), msgs, true, 0.6, hints);

        LlmRouter.Answer answer = llm.complete(req);
        ItineraryPlan plan;
        try {
            plan = PlanJson.parse(mapper, answer.text(), ItineraryPlan.class);
        } catch (IllegalArgumentException first) {
            // One repair attempt: show the model its mistake.
            log.warn("Plan JSON invalid from {}, retrying: {}", answer.provider(), first.getMessage());
            msgs.add(Message.assistant(answer.text()));
            msgs.add(Message.user("That was not valid JSON in the required shape. Reply again with ONLY the JSON object."));
            answer = llm.complete(new LlmRequest(Task.PLAN, req.system(), msgs, true, 0.3, hints));
            try {
                plan = PlanJson.parse(mapper, answer.text(), ItineraryPlan.class);
            } catch (IllegalArgumentException second) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "The AI returned an itinerary I couldn't read. Try asking again.");
            }
        }
        return new PlanResult(repair(plan, intent, center, nearby, forecast), intent, answer.provider());
    }

    // ---------- helpers ----------

    private String buildContext(TripIntent intent, User user, GeoPoint center, List<Place> nearby, List<DayWeather> forecast) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("trip", intent);
        Map<String, Object> traveller = new LinkedHashMap<>();
        traveller.put("homeCity", user.getHomeCity());
        traveller.put("travelStyle", Optional.ofNullable(user.getTravelStyle()).orElse("comfort"));
        traveller.put("interests", user.interestList());
        ctx.put("traveller", traveller);
        if (center != null) ctx.put("center", Map.of("name", center.displayName(), "lat", center.lat(), "lng", center.lng()));
        ctx.put("places", nearby);
        if (!forecast.isEmpty()) ctx.put("weather", forecast);
        return "TRIP CONTEXT:\n" + PlanJson.write(mapper, ctx);
    }

    /** Make the model's output safe to render: sequential days, sane coordinates, honest totals. */
    ItineraryPlan repair(ItineraryPlan plan, TripIntent intent, GeoPoint center, List<Place> nearby, List<DayWeather> forecast) {
        Map<String, Place> byName = new HashMap<>();
        for (Place p : nearby) byName.put(p.name().toLowerCase(Locale.ROOT), p);

        List<DayPlan> days = new ArrayList<>();
        List<DayPlan> src = plan.days() == null ? List.of() : plan.days();
        for (int i = 0; i < src.size() && i < TripIntent.MAX_DAYS; i++) {
            DayPlan d = src.get(i);
            List<Activity> acts = new ArrayList<>();
            for (Activity a : d.activities() == null ? List.<Activity>of() : d.activities()) {
                if (a == null || a.name() == null || a.name().isBlank()) continue;
                Double lat = a.lat(), lng = a.lng();
                Place match = byName.get(a.name().toLowerCase(Locale.ROOT));
                if (match != null) {
                    lat = match.lat();
                    lng = match.lng();
                } else if (lat == null || lng == null || (center != null && km(center.lat(), center.lng(), lat, lng) > MAX_DISTANCE_KM)) {
                    lat = null;     // don't drop a pin in the wrong state; the card still shows
                    lng = null;
                }
                Integer cost = a.estCostInr() == null ? null : Math.max(0, a.estCostInr());
                String category = a.category() == null ? "sight" : a.category().toLowerCase(Locale.ROOT);
                acts.add(new Activity(a.time(), a.name().trim(), a.description(), category, lat, lng, cost));
            }
            days.add(new DayPlan(i + 1, d.theme(), acts));
        }
        if (days.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI returned an empty itinerary. Try asking again.");
        }
        String destination = plan.destination() == null || plan.destination().isBlank() ? intent.destination() : plan.destination();
        String title = plan.title() == null || plan.title().isBlank() ? days.size() + " days in " + destination : plan.title();
        return new ItineraryPlan(title, destination, plan.summary(),
                center != null ? Double.valueOf(center.lat()) : plan.centerLat(),
                center != null ? Double.valueOf(center.lng()) : plan.centerLng(),
                days, plan.estimatedTotalInr(), plan.tips(), forecast.isEmpty() ? null : forecast)
                .withRecomputedTotal();
    }

    static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
