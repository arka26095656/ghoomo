package com.ghoomo.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "home_city")
    private String homeCity;

    /** e.g. "budget", "comfort", "luxury" */
    @Column(name = "travel_style")
    private String travelStyle;

    /** Typical per-trip budget in INR. */
    @Column(name = "budget_inr")
    private Integer budgetInr;

    /** Comma-separated list, e.g. "beaches,food,trekking". */
    private String interests;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {}

    public User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public List<String> interestList() {
        if (interests == null || interests.isBlank()) return List.of();
        return Arrays.stream(interests.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setInterestList(List<String> list) {
        this.interests = list == null ? null : String.join(",", list.stream().map(String::trim).toList());
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHomeCity() { return homeCity; }
    public void setHomeCity(String homeCity) { this.homeCity = homeCity; }
    public String getTravelStyle() { return travelStyle; }
    public void setTravelStyle(String travelStyle) { this.travelStyle = travelStyle; }
    public Integer getBudgetInr() { return budgetInr; }
    public void setBudgetInr(Integer budgetInr) { this.budgetInr = budgetInr; }
    public Instant getCreatedAt() { return createdAt; }
}
