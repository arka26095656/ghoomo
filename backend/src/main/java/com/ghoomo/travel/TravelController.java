package com.ghoomo.travel;

import com.ghoomo.common.ApiException;
import com.ghoomo.itinerary.ItineraryPlan.DayWeather;
import com.ghoomo.travel.TravelModels.CurrencyQuote;
import com.ghoomo.travel.TravelModels.GeoPoint;
import com.ghoomo.travel.TravelModels.Place;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/travel")
@Validated
@Tag(name = "Travel data", description = "Thin, cached wrappers over free public APIs")
public class TravelController {

    private final GeocodingClient geocoding;
    private final PlacesClient places;
    private final WeatherClient weather;
    private final CurrencyClient currency;

    public TravelController(GeocodingClient geocoding, PlacesClient places, WeatherClient weather, CurrencyClient currency) {
        this.geocoding = geocoding;
        this.places = places;
        this.weather = weather;
        this.currency = currency;
    }

    @GetMapping("/geocode")
    @Operation(summary = "Find coordinates for a place name (OpenStreetMap Nominatim)")
    public GeoPoint geocode(@RequestParam @NotBlank @Size(max = 120) String q) {
        return geocoding.geocode(q).orElseThrow(() -> ApiException.notFound("A place called \"" + q + "\""));
    }

    @GetMapping("/places")
    @Operation(summary = "Attractions, nature spots and restaurants near a city (Overpass API)")
    public List<Place> places(@RequestParam @NotBlank String city,
                              @RequestParam(defaultValue = "15") @Min(1) @Max(40) int radiusKm,
                              @RequestParam(required = false) String category) {
        GeoPoint p = geocode(city);
        List<Place> all = places.nearby(p.lat(), p.lng(), radiusKm);
        return category == null ? all : all.stream().filter(x -> x.category().equalsIgnoreCase(category)).toList();
    }

    @GetMapping("/weather")
    @Operation(summary = "Daily forecast for up to 16 days ahead (Open-Meteo)")
    public List<DayWeather> weather(@RequestParam @NotBlank String city,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(defaultValue = "5") @Min(1) @Max(16) int days) {
        GeoPoint p = geocode(city);
        return weather.forecast(p.lat(), p.lng(), from, days);
    }

    @GetMapping("/currency")
    @Operation(summary = "Convert an amount between currencies (Frankfurter / ECB rates)")
    public CurrencyQuote currency(@RequestParam @Pattern(regexp = "[A-Za-z]{3}") String from,
                                  @RequestParam @Pattern(regexp = "[A-Za-z]{3}") String to,
                                  @RequestParam(defaultValue = "1") @Positive double amount) {
        return currency.convert(from, to, amount);
    }
}
