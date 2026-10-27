package com.ghoomo.travel;

import com.ghoomo.travel.TravelModels.GeoPoint;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Offline coordinates for popular Indian destinations.
 * Used when Nominatim is unreachable or rate-limits us, and by the demo-mode intent parser.
 */
public final class Gazetteer {
    private Gazetteer() {}

    private static final Map<String, GeoPoint> PLACES = new LinkedHashMap<>();

    static {
        add("Goa", 15.4909, 73.8278);
        add("Jaipur", 26.9124, 75.7873);
        add("Udaipur", 24.5854, 73.7125);
        add("Jodhpur", 26.2389, 73.0243);
        add("Jaisalmer", 26.9157, 70.9083);
        add("Manali", 32.2432, 77.1892);
        add("Shimla", 31.1048, 77.1734);
        add("Rishikesh", 30.0869, 78.2676);
        add("Varanasi", 25.3176, 82.9739);
        add("Agra", 27.1767, 78.0081);
        add("Delhi", 28.6139, 77.2090);
        add("Mumbai", 19.0760, 72.8777);
        add("Kolkata", 22.5726, 88.3639);
        add("Darjeeling", 27.0410, 88.2663);
        add("Gangtok", 27.3389, 88.6065);
        add("Puri", 19.8135, 85.8312);
        add("Shillong", 25.5788, 91.8933);
        add("Leh", 34.1526, 77.5771);
        add("Munnar", 10.0889, 77.0595);
        add("Alleppey", 9.4981, 76.3388);
        add("Kochi", 9.9312, 76.2673);
        add("Ooty", 11.4102, 76.6950);
        add("Coorg", 12.3375, 75.8069);
        add("Hampi", 15.3350, 76.4600);
        add("Pondicherry", 11.9416, 79.8083);
        add("Bengaluru", 12.9716, 77.5946);
        add("Hyderabad", 17.3850, 78.4867);
        add("Chennai", 13.0827, 80.2707);
        add("Andaman", 11.6234, 92.7265);
        add("Mysuru", 12.2958, 76.6394);
        add("Amritsar", 31.6340, 74.8723);
        add("Mussoorie", 30.4598, 78.0644);
        add("Nainital", 29.3919, 79.4542);
        add("Kasol", 32.0100, 77.3150);
        add("Sundarbans", 21.9497, 89.1833);
    }

    private static void add(String name, double lat, double lng) {
        PLACES.put(name.toLowerCase(Locale.ROOT), new GeoPoint(name, name + ", India", lat, lng));
    }

    public static Optional<GeoPoint> lookup(String query) {
        if (query == null) return Optional.empty();
        String q = query.toLowerCase(Locale.ROOT).trim();
        GeoPoint exact = PLACES.get(q);
        if (exact != null) return Optional.of(exact);
        return PLACES.entrySet().stream().filter(e -> q.contains(e.getKey())).map(Map.Entry::getValue).findFirst();
    }

    /** First known destination mentioned in free text, or null. */
    public static String findIn(String text) {
        if (text == null) return null;
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ") + " ";
        // Skip the origin ("from Kolkata") so "Kolkata to Goa" resolves to Goa.
        for (var e : PLACES.entrySet()) {
            String key = e.getKey();
            if (t.contains(" " + key + " ") && !t.contains(" from " + key + " ")) return e.getValue().name();
        }
        return null;
    }
}
