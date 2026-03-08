package org.example.ymltoproperties;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SameOriginApiFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isApiWriteRequest(request)) {
            String origin = request.getHeader("Origin");
            if (origin == null || !origin.equals(resolveRequestOrigin(request))) {
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

    private String resolveRequestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        String host = request.getServerName();

        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);

        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
