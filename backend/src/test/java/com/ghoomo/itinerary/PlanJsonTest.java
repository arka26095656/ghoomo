package com.ghoomo.itinerary;

import com.ghoomo.config.HttpConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanJsonTest {

    @Test
    void extractsFromMarkdownFence() {
        String raw = "```json\n{\"a\":1}\n```";
        assertThat(PlanJson.extractObject(raw)).isEqualTo("{\"a\":1}");
    }

    @Test
    void extractsWhenModelAddsChatter() {
        String raw = "Sure! Here is your plan:\n{\"title\":\"x\"}\nEnjoy!";
        assertThat(PlanJson.extractObject(raw)).isEqualTo("{\"title\":\"x\"}");
    }

    @Test
    void rejectsReplyWithoutJson() {
        assertThatThrownBy(() -> PlanJson.extractObject("I can't help with that"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesPlanAndIgnoresUnknownFields() {
        String raw = """
                {"title":"Goa","destination":"Goa","extra":"ignored",
                 "days":[{"day":1,"theme":"Beaches","activities":[
                   {"time":"09:00","name":"Baga Beach","category":"nature","lat":15.55,"lng":73.75,"estCostInr":0},
                   {"time":"13:00","name":"Lunch","category":"food","estCostInr":"800"}]}]}
                """;
        ItineraryPlan plan = PlanJson.parse(HttpConfig.lenientMapper(), raw, ItineraryPlan.class);
        assertThat(plan.days()).hasSize(1);
        assertThat(plan.days().get(0).activities().get(1).estCostInr()).isEqualTo(800);
        assertThat(plan.withRecomputedTotal().estimatedTotalInr()).isEqualTo(800);
    }

    @Test
    void rejectsDuplicateKeysSoMergedDaysAreCaught() {
        // What a model produces when it crams three days into one object.
        String raw = """
                {"title":"x","destination":"Jaipur","days":[
                  {"day":1,"theme":"Forts","activities":[],"day":2,"theme":"Markets","activities":[]}]}
                """;
        assertThatThrownBy(() -> PlanJson.parse(HttpConfig.lenientMapper(), raw, ItineraryPlan.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate field");
    }
}
