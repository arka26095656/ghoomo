package com.ghoomo.itinerary;

import com.ghoomo.ai.LlmRequest.Message;
import com.ghoomo.common.RateLimiter;
import com.ghoomo.itinerary.ItineraryDtos.*;
import com.ghoomo.planner.TripIntent;
import com.ghoomo.planner.TripPlanner;
import com.ghoomo.security.CurrentUser;
import com.ghoomo.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
@Tag(name = "Itineraries")
public class ItineraryController {

    private final ItineraryService service;
    private final TripPlanner planner;
    private final CurrentUser currentUser;
    private final RateLimiter rateLimiter;

    public ItineraryController(ItineraryService service, TripPlanner planner, CurrentUser currentUser, RateLimiter rateLimiter) {
        this.service = service;
        this.planner = planner;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate and save an itinerary from one prompt (non-streaming; the chat endpoint streams)")
    public Detail generate(@Valid @RequestBody GenerateRequest req) {
        User user = currentUser.require();
        rateLimiter.check(user.getId());
        TripIntent intent = planner.detectIntent(List.of(Message.user(req.prompt())));
        if (intent.destination() == null || intent.destination().isBlank()) {
            throw new com.ghoomo.common.ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Add a destination to your request, for example \"3 days in Jaipur under ₹15,000\".");
        }
        TripIntent forced = new TripIntent("plan_trip", intent.destination(), intent.origin(), intent.days(),
                intent.budgetInr(), intent.startDate(), intent.travelers(), intent.interests());
        return service.create(user.getId(), planner.plan(forced, user, s -> {}));
    }

    @GetMapping
    @Operation(summary = "List my saved itineraries, newest first")
    public List<Summary> list() {
        return service.list(currentUser.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one itinerary with its full plan")
    public Detail get(@PathVariable Long id) {
        return service.get(currentUser.id(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an itinerary after editing (title, dates, reordered activities)")
    public Detail update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        return service.update(currentUser.id(), id, req);
    }

    @PatchMapping("/{id}/days/{day}")
    @Operation(summary = "Replace the activities of a single day")
    public Detail updateDay(@PathVariable Long id, @PathVariable int day, @Valid @RequestBody UpdateDayRequest req) {
        return service.updateDay(currentUser.id(), id, day, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an itinerary")
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.id(), id);
    }
}
