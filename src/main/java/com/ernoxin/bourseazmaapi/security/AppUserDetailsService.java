package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsernameOrEmail(normalized, normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return AppUserPrincipal.from(user);
    }

    public AppUserPrincipal loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return AppUserPrincipal.from(user);
    }
}
