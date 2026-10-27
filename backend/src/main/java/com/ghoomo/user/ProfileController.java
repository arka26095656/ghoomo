package com.ghoomo.user;

import com.ghoomo.security.CurrentUser;
import com.ghoomo.user.UserDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Profile & preferences")
public class ProfileController {

    private final UserRepository users;
    private final CurrentUser currentUser;

    public ProfileController(UserRepository users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Current user's profile and travel preferences")
    public Profile me() {
        return Profile.of(currentUser.require());
    }

    @PutMapping
    @Transactional
    @Operation(summary = "Update profile and travel preferences (used to personalise the AI)")
    public Profile update(@Valid @RequestBody UpdateProfileRequest req) {
        User user = currentUser.require();
        user.setName(req.name().trim());
        user.setHomeCity(blankToNull(req.homeCity()));
        user.setTravelStyle(blankToNull(req.travelStyle()));
        user.setBudgetInr(req.budgetInr());
        user.setInterestList(req.interests());
        return Profile.of(users.save(user));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
