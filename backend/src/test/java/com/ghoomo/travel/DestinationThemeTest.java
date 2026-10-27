package com.ghoomo.travel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationThemeTest {

    @Test
    void matchesPlaceNamesCaseInsensitively() {
        assertThat(DestinationTheme.resolve("Jaipur", null, null)).isEqualTo("rajasthan");
        assertThat(DestinationTheme.resolve("Alleppey, Kerala", null, null)).isEqualTo("kerala");
        assertThat(DestinationTheme.resolve("NORTH GOA", null, null)).isEqualTo("goa");
        assertThat(DestinationTheme.resolve("Leh-Ladakh", null, null)).isEqualTo("himalayas");
        assertThat(DestinationTheme.resolve("Kyoto", null, null)).isEqualTo("japan");
        assertThat(DestinationTheme.resolve("Santorini, Greece", null, null)).isEqualTo("santorini");
    }

    @Test
    void doesNotMatchInsideOtherWords() {
        // "goa" must not match "Goalpara", "leh" must not match "Lehigh"
        assertThat(DestinationTheme.resolve("Goalpara", null, null)).isEqualTo(DestinationTheme.DEFAULT);
        assertThat(DestinationTheme.resolve("Lehigh Valley", null, null)).isEqualTo(DestinationTheme.DEFAULT);
    }

    @Test
    void fallsBackToCoordinatesForUnlistedPlaces() {
        // Mandawa, Rajasthan
        assertThat(DestinationTheme.resolve("Mandawa", 28.05, 75.15)).isEqualTo("rajasthan");
        // Chopta, Uttarakhand
        assertThat(DestinationTheme.resolve("Chopta", 30.49, 79.21)).isEqualTo("himalayas");
        // Takayama, Japan
        assertThat(DestinationTheme.resolve("Takayama", 36.14, 137.25)).isEqualTo("japan");
    }

    @Test
    void usesDefaultWhenNothingMatches() {
        assertThat(DestinationTheme.resolve("Kolkata", 22.57, 88.36)).isEqualTo(DestinationTheme.DEFAULT);
        assertThat(DestinationTheme.resolve("Amritsar", 31.63, 74.87)).isEqualTo(DestinationTheme.DEFAULT);
        // Neighbours of Rajasthan's rough boxes
        assertThat(DestinationTheme.resolve("Delhi", 28.61, 77.21)).isEqualTo(DestinationTheme.DEFAULT);
        assertThat(DestinationTheme.resolve("Agra", 27.18, 78.01)).isEqualTo(DestinationTheme.DEFAULT);
        assertThat(DestinationTheme.resolve("Ujjain", 23.18, 75.78)).isEqualTo(DestinationTheme.DEFAULT);
        assertThat(DestinationTheme.resolve(null, null, null)).isEqualTo(DestinationTheme.DEFAULT);
    }
}
