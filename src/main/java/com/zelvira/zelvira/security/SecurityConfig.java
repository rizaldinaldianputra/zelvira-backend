package com.zelvira.zelvira.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 1. Public API documentation & Swagger UI
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**")
                        .permitAll()

                        // 2. Public authentication, OAuth callbacks, & Webhooks
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/admin/auth/**",
                                "/auth/**",
                                "/login/**",
                                "/api/v1/waha/webhook",
                                "/api/v1/callbacks/**",
                                "/api/callbacks/**",
                                "/api/v1/orders/number/*/sync-payment",
                                "/api/orders/number/*/sync-payment",
                                "/api/v1/orders/*/sync-payment",
                                "/api/orders/*/sync-payment",
                                "/api/digiflazz/webhook",
                                "/api/v1/social/callback/**",
                                "/api/social/callback/**",
                                "/api/marketplace/callback/**",
                                "/api/v1/marketplace/callback/**",
                                "/api/marketplace/webhook/**",
                                "/api/v1/marketplace/webhook/**",
                                "/api/marketplace/*/webhook",
                                "/api/v1/marketplace/*/webhook",
                                "/api/v1/chat/**",
                                "/ws-chat/**",
                                "/ws-chat-native/**",
                                "/ws-chat-native",
                                "/ws-chat",
                                "/uploads/**",
                                "/actuator/health",
                                "/error/**",
                                "/error")
                        .permitAll()

                        // 3. Public Shipping Rate & Tracking calculation (Storefront & Mobile)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/shipping/cost",
                                "/api/v1/shipping/track")
                        .permitAll()

                        // 4. Public Storefront & Mobile Read-Only (GET) Endpoints
                        // Mutations (POST, PUT, DELETE, PATCH) on these resources require authentication!
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/products/digital",
                                "/api/products/digital/**",
                                "/api/v1/categories",
                                "/api/v1/categories/**",
                                "/api/v1/brands",
                                "/api/v1/brands/**",
                                "/api/v1/reviews",
                                "/api/v1/reviews/**",
                                "/api/news",
                                "/api/news/**",
                                "/api/v1/content/**",
                                "/api/v1/content/sections/**",
                                "/api/v1/seo-configs",
                                "/api/v1/seo-configs/**",
                                "/api/flash-sales",
                                "/api/flash-sales/**",
                                "/api/organization",
                                "/api/settings/mobile",
                                "/api/settings/mobile/**",
                                "/api/v1/faqs",
                                "/api/v1/faqs/**",
                                "/api/v1/privacy-policy",
                                "/api/v1/privacy-policy/**",
                                "/api/v1/help-articles",
                                "/api/v1/help-articles/**",
                                "/api/v1/locations/**",
                                "/api/v1/shipping/**",
                                "/api/vouchers",
                                "/api/vouchers/all",
                                "/api/vouchers/code/**")
                        .permitAll()

                        // 5. All other endpoints (including all admin & user mutations) require authentication
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, authException) -> response
                                        .sendError(401, "Unauthorized"),
                                new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                                        "/api/**")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:3002",
                "http://localhost:3003",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:3001",
                "http://127.0.0.1:3002",
                "http://127.0.0.1:3003",
                "https://zelvira.my.id",
                "https://lawfirm.zelvira.my.id",
                "https://admin.zelvira.my.id",
                "https://minio.zelvira.my.id",
                "https://storage.zelvira.my.id"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
