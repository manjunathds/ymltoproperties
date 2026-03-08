package org.example.ymltoproperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${app.security.require-https:false}") boolean requireHttps,
                                            @Value("${app.security.rate-limit.max-requests:30}") int maxRequests,
                                            @Value("${app.security.rate-limit.window-seconds:60}") int windowSeconds) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' https://fonts.googleapis.com; " +
                                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                        "font-src 'self' https://fonts.gstatic.com; " +
                                        "img-src 'self' data:; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());

        if (requireHttps) {
            http.addFilterBefore(new HttpsRedirectFilter(), AuthorizationFilter.class);
        }

        http.addFilterBefore(new SameOriginApiFilter(), AuthorizationFilter.class);
        http.addFilterBefore(new ApiRateLimitFilter(maxRequests, windowSeconds), AuthorizationFilter.class);

        return http.build();
    }

    static final class HttpsRedirectFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            boolean isSecureRequest = request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
            if (!isSecureRequest) {
                String query = request.getQueryString();
                String redirectUrl = "https://" + request.getServerName() + request.getRequestURI()
                        + (query != null ? "?" + query : "");
                response.setStatus(HttpServletResponse.SC_PERMANENT_REDIRECT);
                response.setHeader("Location", redirectUrl);
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
