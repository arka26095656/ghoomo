package com.ghoomo.travel;

/** Small value types returned by the travel clients. */
public final class TravelModels {
    private TravelModels() {}

    public record GeoPoint(String name, String displayName, double lat, double lng) {}

    /**
     * A point of interest from OpenStreetMap.
     * category is normalised to the same set the itinerary uses: sight, food, nature, culture, shopping, nightlife, adventure.
     */
    public record Place(String name, String category, double lat, double lng) {}

    public record CurrencyQuote(String from, String to, double amount, double rate, double converted, String date) {}
}
