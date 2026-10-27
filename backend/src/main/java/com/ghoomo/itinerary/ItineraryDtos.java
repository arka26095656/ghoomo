package com.ghoomo.itinerary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ItineraryDtos {
    private ItineraryDtos() {}

    /** Card on the dashboard: no plan body. */
    public record Summary(Long id, String title, String destination, LocalDate startDate, int days,
                          Integer budgetInr, Integer estimatedInr, Instant updatedAt) {}

    public record Detail(Long id, String title, String destination, LocalDate startDate, int days,
                         Integer budgetInr, Integer estimatedInr, Instant createdAt, Instant updatedAt,
                         ItineraryPlan plan,
                         /** Colour theme for the UI, worked out from the destination. See DestinationTheme. */
                         String themeKey) {}

    public record GenerateRequest(
            @NotBlank @Size(max = 1000) String prompt) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            LocalDate startDate,
            @Min(1000) Integer budgetInr,
            @NotNull @Valid ItineraryPlan plan) {}

    public record UpdateDayRequest(
            @Size(max = 200) String theme,
            @NotNull @Size(max = 12) List<ItineraryPlan.@Valid Activity> activities) {}
}
