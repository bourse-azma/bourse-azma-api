package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String phoneNumber;
    private final String password;
    private final UserRole role;
    private final boolean enabled;
    private final long tokenVersion;

    private AppUserPrincipal(Long id, String username, String email, String phoneNumber, String password,
                             UserRole role, boolean enabled, long tokenVersion) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.tokenVersion = tokenVersion;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getPassword(),
                user.getRole(),
                !user.isBlocked() && user.getDeletedAt() == null,
                user.getTokenVersion()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username != null ? username : (email != null ? email : phoneNumber);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
