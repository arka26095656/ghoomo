package com.ghoomo.ai;

import com.ghoomo.ai.LlmRequest.Message;
import com.ghoomo.config.HttpConfig;
import com.ghoomo.planner.TripIntent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmProviderTest {

    private final MockLlmProvider mock = new MockLlmProvider(HttpConfig.lenientMapper());

    private TripIntent intentFor(Message... turns) {
        return mock.extractIntent(new LlmRequest(LlmRequest.Task.INTENT, "", List.of(turns), true, 0, Map.of()));
    }

    @Test
    void readsDestinationDaysAndBudget() {
        TripIntent i = intentFor(Message.user("Plan me a 3-day trip to Jaipur under ₹15,000"));
        assertThat(i.wantsPlan()).isTrue();
        assertThat(i.destination()).isEqualTo("Jaipur");
        assertThat(i.days()).isEqualTo(3);
        assertThat(i.budgetInr()).isEqualTo(15000);
    }

    @Test
    void ignoresOriginCity() {
        TripIntent i = intentFor(Message.user("4 days in Goa from Kolkata under 25k, we love beaches"));
        assertThat(i.destination()).isEqualTo("Goa");
        assertThat(i.origin()).isEqualTo("Kolkata");
        assertThat(i.budgetInr()).isEqualTo(25000);
        assertThat(i.interests()).contains("beach");
    }

    @Test
    void carriesDetailsAcrossTurns() {
        TripIntent i = intentFor(
                Message.user("Plan 3 days in Udaipur under 20k"),
                Message.assistant("Here's your plan..."),
                Message.user("make it 5 days"));
        assertThat(i.wantsPlan()).isTrue();
        assertThat(i.destination()).isEqualTo("Udaipur");
        assertThat(i.days()).isEqualTo(5);
        assertThat(i.budgetInr()).isEqualTo(20000);
    }

    @Test
    void greetingIsChat() {
        assertThat(intentFor(Message.user("hello there")).wantsPlan()).isFalse();
    }
}
