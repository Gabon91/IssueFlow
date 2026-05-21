package com.att.tdp.issueflow.common.security;

import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring-Security principal wrapping an IssueFlow {@link User}. Authorities are derived from
 * the user's {@link Role} and prefixed with {@code ROLE_} so {@code hasRole('ADMIN')} works.
 */
public class IssueFlowUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final Role role;

    public IssueFlowUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
    }

    public IssueFlowUserDetails(Long userId, String username, Role role) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = "";
        this.role = role;
    }

    public Long getUserId() { return userId; }

    public Role getRole() { return role; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
