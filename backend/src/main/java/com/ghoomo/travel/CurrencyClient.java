package com.ghoomo.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghoomo.common.ApiException;
import com.ghoomo.config.AppProperties;
import com.ghoomo.travel.TravelModels.CurrencyQuote;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Locale;

/** Exchange rates from Frankfurter (European Central Bank reference rates, free, no key). */
@Component
public class CurrencyClient {

    private final RestClient rest;

    public CurrencyClient(AppProperties props, RestClient.Builder builder) {
        this.rest = builder.clone()
                .baseUrl(props.travel().frankfurterUrl())
                .requestFactory(GeocodingClient.TravelHttp.factory())
                .build();
    }

    @Cacheable(cacheNames = "fx", key = "#from + ':' + #to")
    public double rate(String from, String to) {
        String f = from.toUpperCase(Locale.ROOT), t = to.toUpperCase(Locale.ROOT);
        if (f.equals(t)) return 1.0;
        try {
            JsonNode root = rest.get()
                    .uri(u -> u.path("/latest").queryParam("base", f).queryParam("symbols", t).build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode r = root == null ? null : root.path("rates").path(t);
            if (r == null || r.isMissingNode()) throw new IllegalStateException("no rate");
            return r.asDouble();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Couldn't fetch the " + f + "→" + t + " rate right now. Check the currency codes or try again.");
        }
    }

    public CurrencyQuote convert(String from, String to, double amount) {
        double rate = rate(from, to);
        return new CurrencyQuote(from.toUpperCase(Locale.ROOT), to.toUpperCase(Locale.ROOT), amount, rate,
                Math.round(amount * rate * 100.0) / 100.0, java.time.LocalDate.now().toString());
    }
}
