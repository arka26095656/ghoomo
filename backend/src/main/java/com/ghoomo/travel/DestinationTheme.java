package com.ghoomo.travel;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Picks the colour theme the UI uses for a trip, from its destination.
 * <p>
 * Two passes: a place name in the destination text ("Udaipur" → rajasthan), then, for places not
 * in the list, the geocoded centre falling inside a region's rough bounding box. Anything else gets
 * {@link #DEFAULT}. The keys must match the {@code data-dest} themes in the frontend's index.css.
 */
public final class DestinationTheme {
    private DestinationTheme() {}

    public static final String DEFAULT = "default";

    private record Box(double minLat, double maxLat, double minLng, double maxLng) {
        boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }
    }

    private record Region(String key, Pattern names, List<Box> boxes) {}

    private static Region region(String key, List<Box> boxes, String... names) {
        String alternation = String.join("|", names);
        return new Region(key, Pattern.compile("\\b(" + alternation + ")\\b"), boxes);
    }

    // Order matters only where boxes overlap; names are checked for every region before any box.
    private static final List<Region> REGIONS = List.of(
            region("rajasthan",
                    List.of(new Box(24.3, 30.0, 69.5, 76.4),   // west and north, stops short of Delhi NCR
                            new Box(24.0, 27.0, 76.4, 77.9)),  // south-east, stops short of Agra and MP
                    "rajasthan", "jaipur", "udaipur", "jodhpur", "jaisalmer", "pushkar", "ajmer", "bikaner",
                    "mount abu", "chittorgarh", "ranthambore", "sawai madhopur", "bundi", "kumbhalgarh"),
            region("kerala", List.of(),
                    "kerala", "munnar", "alleppey", "alappuzha", "kochi", "cochin", "kumarakom", "varkala",
                    "wayanad", "thekkady", "kovalam", "trivandrum", "thiruvananthapuram", "kozhikode",
                    "kannur", "vagamon", "athirappilly", "kollam"),
            region("goa", List.of(new Box(14.85, 15.82, 73.6, 74.35)),
                    "goa", "panaji", "panjim", "calangute", "anjuna", "baga", "palolem", "vagator",
                    "candolim", "margao", "arambol"),
            region("himalayas",
                    List.of(new Box(29.3, 37.5, 76.5, 81.0),   // Himachal, Uttarakhand, Ladakh
                            new Box(32.5, 37.5, 73.5, 76.5),   // Kashmir
                            new Box(26.8, 29.5, 87.9, 97.5)),  // Darjeeling, Sikkim, Arunachal
                    "himalaya", "himalayas", "himachal", "uttarakhand", "ladakh", "leh", "manali", "shimla",
                    "kasol", "spiti", "dharamshala", "mcleodganj", "mussoorie", "nainital", "rishikesh",
                    "auli", "kashmir", "srinagar", "gulmarg", "pahalgam", "darjeeling", "gangtok", "sikkim",
                    "tawang", "nepal", "kathmandu", "pokhara", "bhutan", "thimphu", "paro"),
            region("japan", List.of(new Box(24.0, 46.0, 122.9, 146.0)),
                    "japan", "tokyo", "kyoto", "osaka", "nara", "hiroshima", "hokkaido", "sapporo",
                    "hakone", "nikko", "kanazawa", "fukuoka", "okinawa", "yokohama"),
            region("santorini", List.of(),
                    "santorini", "mykonos", "greece", "athens", "crete", "naxos", "paros", "rhodes", "corfu"));

    /** Theme key for a destination name and optional geocoded centre. Never null. */
    public static String resolve(String destination, Double lat, Double lng) {
        if (destination != null && !destination.isBlank()) {
            String text = destination.toLowerCase(Locale.ROOT);
            for (Region r : REGIONS) {
                if (r.names().matcher(text).find()) return r.key();
            }
        }
        if (lat != null && lng != null) {
            for (Region r : REGIONS) {
                for (Box b : r.boxes()) {
                    if (b.contains(lat, lng)) return r.key();
                }
            }
        }
        return DEFAULT;
    }
}
