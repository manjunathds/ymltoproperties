package org.example.ymltoproperties;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;

public class SameOriginApiFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isApiWriteRequest(request)) {
            String sourceOrigin = resolveSourceOrigin(request);
            if (sourceOrigin == null || !sourceOrigin.equalsIgnoreCase(resolveRequestOrigin(request))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("text/plain");
                response.getWriter().write("Blocked by origin policy");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApiWriteRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return false;
        }
        return request.getRequestURI().startsWith("/api/");
    }

    private String resolveSourceOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin;
        }

        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }

        try {
            URI refererUri = URI.create(referer);
            int port = refererUri.getPort();
            boolean defaultPort = ("http".equalsIgnoreCase(refererUri.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(refererUri.getScheme()) && port == 443)
                    || port == -1;
            return defaultPort
                    ? refererUri.getScheme() + "://" + refererUri.getHost()
                    : refererUri.getScheme() + "://" + refererUri.getHost() + ":" + port;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveRequestOrigin(HttpServletRequest request) {
        String forwardedProto = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        String forwardedHost = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        String forwardedPort = firstHeaderValue(request.getHeader("X-Forwarded-Port"));

        String scheme = forwardedProto != null ? forwardedProto : request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        if (forwardedHost != null) {
            String[] hostParts = forwardedHost.split(":", 2);
            host = hostParts[0];
            if (hostParts.length == 2) {
                port = parsePort(hostParts[1], port);
            } else if (forwardedPort == null) {
                port = defaultPortForScheme(scheme, port);
            }
        }

        if (forwardedPort != null) {
            port = parsePort(forwardedPort, port);
        } else if (forwardedProto != null && forwardedHost == null) {
            port = defaultPortForScheme(scheme, port);
        }

        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);

        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private String firstHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return headerValue.split(",", 2)[0].trim();
    }

    private int parsePort(String rawPort, int fallbackPort) {
        try {
            return Integer.parseInt(rawPort.trim());
        } catch (NumberFormatException ex) {
            return fallbackPort;
        }
    }

    private int defaultPortForScheme(String scheme, int fallbackPort) {
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return fallbackPort;
    }
}
