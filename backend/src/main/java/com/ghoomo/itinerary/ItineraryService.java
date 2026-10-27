package com.ghoomo.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghoomo.common.ApiException;
import com.ghoomo.itinerary.ItineraryDtos.*;
import com.ghoomo.planner.TripIntent;
import com.ghoomo.planner.TripPlanner;
import com.ghoomo.travel.DestinationTheme;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ItineraryService {

    private final ItineraryRepository repo;
    private final ObjectMapper mapper;

    public ItineraryService(ItineraryRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional
    public Detail create(Long userId, TripPlanner.PlanResult result) {
        Itinerary it = new Itinerary(userId);
        TripIntent intent = result.intent();
        it.setStartDate(TripPlannerDates.parse(intent.startDate()));
        it.setBudgetInr(intent.budgetInr());
        apply(it, result.plan(), result.plan().title());
        return toDetail(repo.save(it));
    }

    @Transactional(readOnly = true)
    public List<Summary> list(Long userId) {
        return repo.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(i -> new Summary(i.getId(), i.getTitle(), i.getDestination(), i.getStartDate(), i.getDayCount(),
                        i.getBudgetInr(), i.getEstimatedInr(), i.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail get(Long userId, Long id) {
        return toDetail(find(userId, id));
    }

    @Transactional
    public Detail update(Long userId, Long id, UpdateRequest req) {
        Itinerary it = find(userId, id);
        it.setStartDate(req.startDate());
        it.setBudgetInr(req.budgetInr());
        // Keep weather and map center from the stored plan; the client can't invent those.
        ItineraryPlan stored = readPlan(it);
        ItineraryPlan incoming = req.plan();
        ItineraryPlan merged = new ItineraryPlan(req.title(), incoming.destination(), incoming.summary(),
                stored.centerLat(), stored.centerLng(), renumber(incoming.days()), incoming.estimatedTotalInr(),
                incoming.tips(), stored.weather());
        apply(it, merged, req.title());
        return toDetail(repo.save(it));
    }

    @Transactional
    public Detail updateDay(Long userId, Long id, int dayNumber, UpdateDayRequest req) {
        Itinerary it = find(userId, id);
        ItineraryPlan plan = readPlan(it);
        if (dayNumber < 1 || dayNumber > plan.days().size()) throw ApiException.notFound("Day " + dayNumber);
        List<ItineraryPlan.DayPlan> days = new ArrayList<>(plan.days());
        ItineraryPlan.DayPlan old = days.get(dayNumber - 1);
        days.set(dayNumber - 1, new ItineraryPlan.DayPlan(dayNumber,
                req.theme() == null ? old.theme() : req.theme(), req.activities()));
        ItineraryPlan updated = new ItineraryPlan(plan.title(), plan.destination(), plan.summary(), plan.centerLat(),
                plan.centerLng(), days, plan.estimatedTotalInr(), plan.tips(), plan.weather());
        apply(it, updated, it.getTitle());
        return toDetail(repo.save(it));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repo.delete(find(userId, id));
    }

    // ---------- helpers ----------

    private Itinerary find(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Itinerary"));
    }

    private void apply(Itinerary it, ItineraryPlan plan, String title) {
        ItineraryPlan finalPlan = new ItineraryPlan(title, plan.destination(), plan.summary(), plan.centerLat(),
                plan.centerLng(), plan.days(), plan.estimatedTotalInr(), plan.tips(), plan.weather()).withRecomputedTotal();
        it.setTitle(title);
        it.setDestination(finalPlan.destination());
        it.setDayCount(finalPlan.days().size());
        it.setEstimatedInr(finalPlan.estimatedTotalInr());
        it.setPlanJson(PlanJson.write(mapper, finalPlan));
    }

    private static List<ItineraryPlan.DayPlan> renumber(List<ItineraryPlan.DayPlan> days) {
        List<ItineraryPlan.DayPlan> out = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            var d = days.get(i);
            out.add(new ItineraryPlan.DayPlan(i + 1, d.theme(), d.activities() == null ? List.of() : d.activities()));
        }
        return out;
    }

    ItineraryPlan readPlan(Itinerary it) {
        return PlanJson.parse(mapper, it.getPlanJson(), ItineraryPlan.class);
    }

    Detail toDetail(Itinerary it) {
        ItineraryPlan plan = readPlan(it);
        String themeKey = DestinationTheme.resolve(
                plan.destination() != null ? plan.destination() : it.getDestination(),
                plan.centerLat(), plan.centerLng());
        return new Detail(it.getId(), it.getTitle(), it.getDestination(), it.getStartDate(), it.getDayCount(),
                it.getBudgetInr(), it.getEstimatedInr(), it.getCreatedAt(), it.getUpdatedAt(), plan, themeKey);
    }

    /** Tiny shim so this class doesn't depend on TripPlanner internals. */
    static final class TripPlannerDates {
        static java.time.LocalDate parse(String s) {
            try {
                return s == null || s.isBlank() ? null : java.time.LocalDate.parse(s.trim());
            } catch (java.time.format.DateTimeParseException e) {
                return null;
            }
        }
    }
}
