package com.ghoomo.user;

import jakarta.validation.constraints.*;

import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, Profile user) {}

    public record Profile(Long id, String name, String email, String homeCity,
                          String travelStyle, Integer budgetInr, List<String> interests) {
        public static Profile of(User u) {
            return new Profile(u.getId(), u.getName(), u.getEmail(), u.getHomeCity(),
                    u.getTravelStyle(), u.getBudgetInr(), u.interestList());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String homeCity,
            @Pattern(regexp = "budget|comfort|luxury") String travelStyle,
            @Min(1000) @Max(10_000_000) Integer budgetInr,
            @Size(max = 12) List<@NotBlank @Size(max = 40) String> interests) {}
}
