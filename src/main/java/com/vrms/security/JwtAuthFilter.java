package com.vrms.security;

import com.vrms.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Reads "Authorization: Bearer <jwt>" and, if valid, signs the request in as that user. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parseUserId(header.substring(7))
                    .flatMap(userRepository::findById)
                    .ifPresent(user -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
