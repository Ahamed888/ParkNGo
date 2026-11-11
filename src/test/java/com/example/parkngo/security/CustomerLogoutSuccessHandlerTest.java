package com.example.parkngo.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerLogoutSuccessHandlerTest {
    private final CustomerLogoutSuccessHandler handler = new CustomerLogoutSuccessHandler();

    @Test
    void customerGoesToGoodbyePage() throws IOException {
        assertRedirect("/logout/goodbye", "ROLE_CUSTOMER");
    }

    @Test
    void staffAndAdminKeepHomeRedirect() throws IOException {
        assertRedirect("/?logout", "ROLE_STAFF");
        assertRedirect("/?logout", "ROLE_ADMIN");
        assertRedirect("/?logout", "ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void unauthenticatedLogoutKeepsHomeRedirect() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onLogoutSuccess(new MockHttpServletRequest(), response, null);
        assertThat(response.getRedirectedUrl()).isEqualTo("/?logout");
    }

    private void assertRedirect(String expected, String... roles) throws IOException {
        Authentication authentication = new UsernamePasswordAuthenticationToken("user", null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onLogoutSuccess(new MockHttpServletRequest(), response, authentication);
        assertThat(response.getRedirectedUrl()).isEqualTo(expected);
    }
}
