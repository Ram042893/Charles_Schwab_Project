package com.schwab.shortener.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessAuthAndGenericFailures() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/urls/x");

        var business = handler.handleBusiness(new BusinessException(404, "Short URL not found"), request);
        assertThat(business.getStatusCode().value()).isEqualTo(404);
        assertThat(business.getBody().message()).isEqualTo("Short URL not found");
        assertThat(business.getBody().path()).isEqualTo("/api/v1/urls/x");

        var unauthorized = handler.handleBadCredentials(new BadCredentialsException("bad"), request);
        assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);
        assertThat(unauthorized.getBody().message()).isEqualTo("Invalid credentials");

        var denied = handler.handleDenied(new AccessDeniedException("nope"), request);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        var unexpected = handler.handleGeneric(new RuntimeException("boom"), request);
        assertThat(unexpected.getStatusCode().value()).isEqualTo(500);
        assertThat(unexpected.getBody().message()).isEqualTo("Unexpected error");
    }
}
