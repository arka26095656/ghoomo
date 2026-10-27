package com.ghoomo.security;

import com.ghoomo.common.ApiException;
import com.ghoomo.user.User;
import com.ghoomo.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user.id();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Log in to continue.");
    }

    public User require() {
        return users.findById(id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your account no longer exists. Log in again."));
    }
}
