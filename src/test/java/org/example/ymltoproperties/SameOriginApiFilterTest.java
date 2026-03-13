package org.example.ymltoproperties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SameOriginApiFilterTest {

    private final SameOriginApiFilter filter = new SameOriginApiFilter();

    @Test
    void allowsProxyForwardedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/yaml/to-properties");
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "ymltoproperties.example.com");
        request.addHeader("Origin", "https://ymltoproperties.example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsSameOriginRefererWhenOriginHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/yaml/to-properties");
        request.setScheme("https");
        request.setServerName("localhost");
        request.setServerPort(443);
        request.addHeader("Referer", "https://localhost/tools");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksMismatchedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/yaml/to-properties");
        request.setScheme("https");
        request.setServerName("localhost");
        request.setServerPort(443);
        request.addHeader("Origin", "https://evil.example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEqualTo("Blocked by origin policy");
    }
}
