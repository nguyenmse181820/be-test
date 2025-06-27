package com.boeing.bookingservice.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

@Getter
public class AuthenticatedUserPrincipal extends User {
    private final UUID userId;
    private final String email;
    private final String firstName;
    private final String lastName;

    public AuthenticatedUserPrincipal(String username, UUID userId, Collection<? extends GrantedAuthority> authorities) {
        super(username, "", authorities);
        this.userId = userId;
        this.email = username;
        this.firstName = null; // Will be set from JWT if available
        this.lastName = null;  // Will be set from JWT if available
    }
    
    public AuthenticatedUserPrincipal(String username, UUID userId, String firstName, String lastName, 
                                    Collection<? extends GrantedAuthority> authorities) {
        super(username, "", authorities);
        this.userId = userId;
        this.email = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UUID getUserIdAsUUID() {
        return userId;
    }
}