package com.cineverse.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Authenticated caller, reconstructed from the JWT on every request (stateless). */
public record UserPrincipal(Long id, String email, String name, Set<String> roles) {

    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public List<String> roleList() {
        return roles.stream().sorted().toList();
    }
}
