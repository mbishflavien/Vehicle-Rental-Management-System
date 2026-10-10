package com.vrms.config;

import com.vrms.exception.ApiException;
import com.vrms.model.AuthProvider;
import com.vrms.model.User;
import com.vrms.security.JwtService;
import com.vrms.security.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Continue with Google / GitHub": the OAuth2 authorization-code flow. After the provider confirms
 * the user, VRMS issues its own bearer token and hands it to the React app in the URL fragment
 * (never sent to a server or written to logs). Providers are only enabled when their client id is set.
 */
@Configuration
public class OAuth2LoginConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginConfig.class);

    private final List<ClientRegistration> registrations = new ArrayList<>();

    public OAuth2LoginConfig(@Value("${vrms.oauth.google.client-id:}") String googleId,
                             @Value("${vrms.oauth.google.client-secret:}") String googleSecret,
                             @Value("${vrms.oauth.github.client-id:}") String githubId,
                             @Value("${vrms.oauth.github.client-secret:}") String githubSecret) {
        if (!googleId.isBlank()) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleId).clientSecret(googleSecret).scope("openid", "profile", "email").build());
        }
        if (!githubId.isBlank()) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(githubId).clientSecret(githubSecret).scope("read:user", "user:email").build());
        }
        log.info("OAuth2 sign-in providers enabled: {}", providers().isEmpty() ? "none" : providers());
    }

    /** Provider ids for the sign-in buttons, e.g. ["google", "github"]. */
    public List<String> providers() {
        return registrations.stream().map(ClientRegistration::getRegistrationId).toList();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // The repository can't be empty; with no provider configured this placeholder is never routed to.
        return registrations.isEmpty()
                ? registrationId -> null
                : new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository repository) {
        return new InMemoryOAuth2AuthorizedClientService(repository);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain oauth2LoginSecurity(HttpSecurity http, ClientRegistrationRepository repository,
                                                   OAuth2AuthorizedClientService clients, OAuthAccountService accounts,
                                                   JwtService jwtService) throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
            // The short-lived session only holds the OAuth2 "state" between the redirect and the callback.
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        if (registrations.isEmpty()) {
            return http.build();
        }
        http.oauth2Login(o -> o
            .clientRegistrationRepository(repository)
            .authorizedClientService(clients)
            .successHandler((req, res, auth) -> onSuccess(req, res, auth, clients, accounts, jwtService))
            .failureHandler((req, res, ex) -> redirectToSignIn(res, "Sign-in was cancelled or failed. Please try again.")));
        return http.build();
    }

    private void onSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth,
                           OAuth2AuthorizedClientService clients, OAuthAccountService accounts,
                           JwtService jwtService) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) auth;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User principal = token.getPrincipal();
        try {
            AuthProvider provider = "github".equals(registrationId) ? AuthProvider.GITHUB : AuthProvider.GOOGLE;
            String email = provider == AuthProvider.GITHUB
                    ? githubEmail(principal, clients.loadAuthorizedClient(registrationId, token.getName()))
                    : googleEmail(principal);
            String name = principal.getAttribute("name");
            User user = accounts.findOrCreate(provider, email, name);
            res.sendRedirect("/oauth/callback#token=" + URLEncoder.encode(jwtService.issueToken(user), StandardCharsets.UTF_8));
        } catch (ApiException e) {
            redirectToSignIn(res, e.getMessage());
        } finally {
            if (req.getSession(false) != null) {
                req.getSession(false).invalidate();
            }
        }
    }

    private static String googleEmail(OAuth2User user) {
        return Boolean.TRUE.equals(user.getAttribute("email_verified")) ? user.getAttribute("email") : null;
    }

    /** GitHub hides private emails from the profile, so ask the emails API for the verified primary one. */
    private static String githubEmail(OAuth2User user, OAuth2AuthorizedClient client) {
        if (client == null) {
            return null;
        }
        List<Map<String, Object>> emails = RestClient.create().get()
                .uri("https://api.github.com/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (emails == null) {
            return null;
        }
        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("verified")) && Boolean.TRUE.equals(e.get("primary")))
                .map(e -> (String) e.get("email"))
                .findFirst()
                .orElse(null);
    }

    private static void redirectToSignIn(HttpServletResponse res, String message) throws IOException {
        res.sendRedirect("/signin?oauthError=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
