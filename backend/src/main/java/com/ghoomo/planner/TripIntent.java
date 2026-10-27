package com.ghoomo.planner;

import java.util.List;

/**
 * What the user wants, extracted by the LLM from the conversation.
 * intent is "plan_trip" when an itinerary should be (re)generated, otherwise "chat".
 */
public record TripIntent(
        String intent,
        String destination,
        String origin,
        Integer days,
        Integer budgetInr,
        String startDate,      // ISO yyyy-MM-dd or null
        Integer travelers,
        List<String> interests) {

    public static final int MAX_DAYS = 14;

    public boolean wantsPlan() {
        return "plan_trip".equalsIgnoreCase(intent) && destination != null && !destination.isBlank();
    }

    /** Clamp values the model might get wrong. */
    public TripIntent normalised() {
        int d = days == null ? 3 : Math.max(1, Math.min(MAX_DAYS, days));
        Integer t = travelers == null || travelers < 1 ? Integer.valueOf(1) : Integer.valueOf(Math.min(travelers, 20));
        Integer b = budgetInr != null && budgetInr < 500 ? null : budgetInr;
        return new TripIntent(intent, destination == null ? null : destination.trim(), origin, d, b,
                startDate, t, interests == null ? List.of() : interests);
    }
}
