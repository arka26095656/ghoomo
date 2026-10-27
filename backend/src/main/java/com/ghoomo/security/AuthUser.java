package com.ghoomo.security;

/** Lightweight principal stored in the SecurityContext; the full User is loaded only when needed. */
public record AuthUser(Long id, String email) {}
