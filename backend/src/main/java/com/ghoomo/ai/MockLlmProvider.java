package com.ghoomo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghoomo.itinerary.ItineraryPlan;
import com.ghoomo.itinerary.ItineraryPlan.Activity;
import com.ghoomo.itinerary.ItineraryPlan.DayPlan;
import com.ghoomo.itinerary.PlanJson;
import com.ghoomo.planner.TripIntent;
import com.ghoomo.travel.Gazetteer;
import com.ghoomo.travel.TravelModels.GeoPoint;
import com.ghoomo.travel.TravelModels.Place;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic stand-in for an LLM. It lets the whole app run, demo and be tested with no API keys
 * and no internet. It is NOT smart: regexes for intent, a slot-filling scheduler for itineraries.
 */
@Component
@Order(100)
public class MockLlmProvider implements LlmProvider {

    public static final String HINT_INTENT = "intent";
    public static final String HINT_PLACES = "places";
    public static final String HINT_CENTER = "center";
    public static final String HINT_PLAN = "plan";

    private static final Pattern DAYS = Pattern.compile("(\\d{1,2})\\s*-?\\s*(?:days?|nights?|d)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET = Pattern.compile(
            "(?:₹|rs\\.?|inr|under|budget(?:\\s+of)?|within)\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k|lakh|l)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM = Pattern.compile("\\bfrom\\s+([A-Z][a-zA-Z]+)");
    private static final Pattern TO = Pattern.compile("\\b(?:to|in|visit|visiting|around)\\s+([A-Z][a-zA-Z]+(?:\\s[A-Z][a-zA-Z]+)?)");
    private static final Pattern PEOPLE = Pattern.compile("(\\d{1,2})\\s*(?:people|persons|friends|of us|adults|travellers|travelers)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLANNING = Pattern.compile(
            "\\b(plan|trip|itinerary|visit|travel|holiday|vacation|getaway|weekend|make it|change|add a day|days? in)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NOT_PLACES = Set.of("January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December", "Summer", "Winter", "Monsoon", "Diwali", "India");
    private static final List<String> INTEREST_WORDS = List.of("beach", "food", "trek", "hiking", "temple", "history",
            "nightlife", "shopping", "nature", "adventure", "museum", "culture", "wildlife", "photography");

    private final ObjectMapper mapper;

    public MockLlmProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String name() { return "mock"; }

    @Override public boolean isConfigured() { return true; }

    @Override
    public String complete(LlmRequest req) {
        return switch (req.task()) {
            case INTENT -> PlanJson.write(mapper, extractIntent(req));
            case PLAN -> PlanJson.write(mapper, buildPlan(req));
            case SUMMARY -> summary(req);
            case CHAT -> chat(req);
        };
    }

    @Override
    public void stream(LlmRequest req, Consumer<String> onToken) {
        String text = complete(req);
        // Emit word by word so the UI's streaming path is exercised.
        for (String part : text.split("(?<= )")) {
            onToken.accept(part);
            try {
                Thread.sleep(18);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ---------- intent ----------

    TripIntent extractIntent(LlmRequest req) {
        List<String> userTurns = req.messages().stream()
                .filter(m -> m.role() == LlmRequest.Role.USER).map(LlmRequest.Message::content).toList();
        String last = userTurns.isEmpty() ? "" : userTurns.get(userTurns.size() - 1);
        boolean planning = PLANNING.matcher(last).find() || DAYS.matcher(last).find();

        String destination = null, origin = null;
        Integer days = null, budget = null, people = null;
        Set<String> interests = new LinkedHashSet<>();
        // newest first, so later messages override earlier ones
        for (int i = userTurns.size() - 1; i >= 0; i--) {
            String t = userTurns.get(i);
            if (destination == null) destination = findDestination(t);
            if (origin == null) origin = group(FROM, t);
            if (days == null) {
                String d = group(DAYS, t);
                if (d != null) days = Integer.valueOf(d);
                else if (t.toLowerCase().contains("weekend")) days = 2;
            }
            if (budget == null) budget = findBudget(t);
            if (people == null) {
                String p = group(PEOPLE, t);
                if (p != null) people = Integer.valueOf(p);
            }
            String lower = t.toLowerCase();
            INTEREST_WORDS.stream().filter(lower::contains).forEach(interests::add);
        }
        return new TripIntent(planning && destination != null ? "plan_trip" : "chat",
                destination, origin, days, budget, null, people, List.copyOf(interests));
    }

    private static String findDestination(String text) {
        String known = Gazetteer.findIn(text);
        if (known != null) return known;
        Matcher m = TO.matcher(text);
        while (m.find()) {
            String c = m.group(1).trim();
            if (!NOT_PLACES.contains(c.split(" ")[0])) return c;
        }
        return null;
    }

    private static Integer findBudget(String text) {
        Matcher m = BUDGET.matcher(text);
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        String unit = m.group(2) == null ? "" : m.group(2).toLowerCase();
        if (unit.equals("k")) v *= 1_000;
        if (unit.equals("lakh") || unit.equals("l")) v *= 100_000;
        return v >= 500 ? (int) v : null;
    }

    private static String group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    // ---------- itinerary ----------

    @SuppressWarnings("unchecked")
    ItineraryPlan buildPlan(LlmRequest req) {
        TripIntent intent = ((TripIntent) req.hints().get(HINT_INTENT)).normalised();
        GeoPoint center = (GeoPoint) req.hints().get(HINT_CENTER);
        List<Place> places = new ArrayList<>((List<Place>) req.hints().getOrDefault(HINT_PLACES, List.of()));
        double lat = center != null ? center.lat() : 20.59;
        double lng = center != null ? center.lng() : 78.96;
        if (places.size() < intent.days() * 3) places.addAll(syntheticPlaces(intent.destination(), lat, lng));

        Map<String, Deque<Place>> byCat = new HashMap<>();
        for (Place p : places) byCat.computeIfAbsent(p.category(), k -> new ArrayDeque<>()).add(p);

        String[][] slots = {
                {"09:00", "sight", "nature", "culture"},
                {"12:30", "food"},
                {"15:30", "culture", "sight", "shopping", "nature"},
                {"19:30", "food", "nightlife", "shopping"}};
        String[] themes = {"Arrival and old town", "Landmarks and local flavours", "Nature and slow afternoons",
                "Markets and hidden corners", "Day trip and sunset", "Culture deep-dive", "Leisure and departure"};

        List<DayPlan> days = new ArrayList<>();
        for (int d = 1; d <= intent.days(); d++) {
            List<Activity> acts = new ArrayList<>();
            for (String[] slot : slots) {
                Place p = take(byCat, Arrays.copyOfRange(slot, 1, slot.length));
                if (p == null) continue;
                acts.add(new Activity(slot[0], p.name(), describe(p, intent.destination()), p.category(),
                        p.lat(), p.lng(), cost(p.category(), intent)));
            }
            days.add(new DayPlan(d, themes[(d - 1) % themes.length], acts));
        }
        String title = intent.days() + " days in " + intent.destination();
        String summary = "A relaxed " + intent.days() + "-day plan for " + intent.destination()
                + " mixing landmarks, local food and some unhurried time.";
        List<String> tips = List.of(
                "Book stays a couple of weeks ahead for better rates.",
                "Start sightseeing early to beat crowds and the afternoon heat.",
                "Carry cash for small eateries and local transport.",
                "Demo mode: add a GEMINI_API_KEY or GROQ_API_KEY for AI-written plans.");
        return new ItineraryPlan(title, intent.destination(), summary, lat, lng, days, null, tips, null)
                .withRecomputedTotal();
    }

    private static Place take(Map<String, Deque<Place>> byCat, String[] cats) {
        for (String c : cats) {
            Deque<Place> q = byCat.get(c);
            if (q != null && !q.isEmpty()) return q.pollFirst();
        }
        for (Deque<Place> q : byCat.values()) if (!q.isEmpty()) return q.pollFirst();
        return null;
    }

    private static int cost(String category, TripIntent intent) {
        int base = switch (category) {
            case "food" -> 600;
            case "nightlife" -> 1200;
            case "adventure" -> 1800;
            case "shopping" -> 800;
            case "culture", "sight" -> 250;
            default -> 150;
        };
        return base * intent.travelers();
    }

    private static String describe(Place p, String dest) {
        return switch (p.category()) {
            case "food" -> "Try regional dishes at " + p.name() + ".";
            case "nature" -> "Unwind at " + p.name() + ", one of " + dest + "'s greener spots.";
            case "culture" -> "Spend an hour at " + p.name() + " to understand the area's history.";
            case "shopping" -> "Browse local crafts and snacks at " + p.name() + ".";
            case "nightlife" -> "End the day with the evening buzz around " + p.name() + ".";
            default -> "Visit " + p.name() + ", a well-loved stop in " + dest + ".";
        };
    }

    private static List<Place> syntheticPlaces(String dest, double lat, double lng) {
        String[][] templates = {
                {"Old Quarter Walk", "sight"}, {"Central Market", "shopping"}, {"Heritage Museum", "culture"},
                {"Sunset Point", "nature"}, {"Street Food Lane", "food"}, {"Riverside Promenade", "nature"},
                {"Local Thali House", "food"}, {"Craft Bazaar", "shopping"}, {"Hilltop Viewpoint", "sight"},
                {"Night Market", "nightlife"}, {"Botanical Garden", "nature"}, {"Café Corner", "food"},
                {"Clock Tower Square", "sight"}, {"Art Gallery", "culture"}, {"Lakeside Dhaba", "food"},
                {"Temple Lane", "culture"}, {"Seafood Shack", "food"}, {"Rooftop Café", "nightlife"}};
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < templates.length; i++) {
            double angle = i * 2.4;           // golden-angle-ish spread
            double r = 0.01 + 0.004 * i;
            out.add(new Place(dest + " " + templates[i][0], templates[i][1],
                    lat + r * Math.sin(angle), lng + r * Math.cos(angle)));
        }
        return out;
    }

    // ---------- text replies ----------

    private String summary(LlmRequest req) {
        ItineraryPlan plan = (ItineraryPlan) req.hints().get(HINT_PLAN);
        StringBuilder sb = new StringBuilder("Here's a ").append(plan.days().size()).append("-day plan for ")
                .append(plan.destination()).append(". ");
        if (!plan.days().isEmpty() && !plan.days().get(0).activities().isEmpty()) {
            sb.append("You'll start at ").append(plan.days().get(0).activities().get(0).name()).append(". ");
        }
        if (plan.estimatedTotalInr() != null) {
            sb.append("Activities and food come to roughly ₹").append(String.format("%,d", plan.estimatedTotalInr()))
                    .append(", not counting travel and stay. ");
        }
        sb.append("Drag activities to reorder them, or tell me what to change.");
        return sb.toString();
    }

    private String chat(LlmRequest req) {
        String q = req.lastUserMessage().toLowerCase();
        if (q.matches(".*\\b(hi|hello|hey|namaste)\\b.*")) {
            return "Namaste! Tell me where you'd like to go, for how many days and your budget, "
                    + "and I'll draft a day-by-day plan. For example: \"Plan 3 days in Jaipur under ₹15,000\".";
        }
        return "I'm running in demo mode right now, so I can plan trips but can't answer open questions well. "
                + "Try something like \"4 days in Goa from Kolkata under ₹25k, we love beaches and seafood\". "
                + "Adding a free Gemini or Groq API key unlocks full conversations.";
    }
}
