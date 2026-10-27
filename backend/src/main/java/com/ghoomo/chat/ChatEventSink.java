package com.ghoomo.chat;

import com.ghoomo.itinerary.ItineraryDtos.Detail;

/** What a chat turn can tell the client while it runs. Implemented over SSE by the controller. */
public interface ChatEventSink {
    void status(String text);

    void itinerary(Detail itinerary);

    void token(String text);

    void done(Long messageId, String provider);

    void error(String message);
}
