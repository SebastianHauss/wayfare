package com.sebastianhauss.wayfare.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CsrfHeaderFilterTest {

    private final CsrfHeaderFilter filter = new CsrfHeaderFilter();

    @Test
    void blocksMutatingRequest_withoutCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/links/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsMutatingRequest_withCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/links/abc");
        request.addHeader(CsrfHeaderFilter.CSRF_HEADER, CsrfHeaderFilter.CSRF_HEADER_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsAnonymousShorten_withoutCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/shorten");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsSafeMethod_withoutCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
