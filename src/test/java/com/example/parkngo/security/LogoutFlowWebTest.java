package com.example.parkngo.security;

import com.example.parkngo.config.GlobalControllerAdvice;
import com.example.parkngo.user.AuthController;
import com.example.parkngo.user.PasswordResetService;
import com.example.parkngo.user.UserRepository;
import com.example.parkngo.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = GlobalControllerAdvice.class))
@Import(SecurityConfig.class)
class LogoutFlowWebTest {
    @Autowired MockMvc mockMvc;
    @MockBean CustomUserDetailsService userDetailsService;
    @MockBean UserService userService;
    @MockBean UserRepository userRepository;
    @MockBean PasswordResetService passwordResetService;

    @Test
    void customerLogoutRequiresCsrfAndRedirectsToGoodbye() throws Exception {
        mockMvc.perform(post("/logout").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/logout").session(session)
                        .cookie(new Cookie("remember-me", "test-token"))
                        .with(user("customer").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logout/goodbye"))
                .andExpect(result -> {
                    assertThat(session.isInvalid()).isTrue();
                    assertThat(result.getResponse().getCookie("remember-me").getMaxAge()).isZero();
                });
    }

    @Test
    void staffAndAdminLogoutStillRedirectHome() throws Exception {
        for (String role : new String[] {"STAFF", "ADMIN"}) {
            mockMvc.perform(post("/logout").with(user(role.toLowerCase()).roles(role)).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/?logout"));
        }
    }

    @Test
    void goodbyePageIsPublicOnlyAfterAuthenticationIsGone() throws Exception {
        mockMvc.perform(get("/logout/goodbye"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/logout-goodbye"));
        mockMvc.perform(get("/logout/goodbye").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void legacyRememberMeCookieDoesNotSignInOnItsOwn() throws Exception {
        mockMvc.perform(get("/login").cookie(new Cookie("remember-me", "legacy-token")))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
    }

    @Test
    void existingSessionCanOpenSignInPageToSwitchAccounts() throws Exception {
        mockMvc.perform(get("/login").with(user("staff@example.com").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
    }

    @Test
    void passwordRecoveryPagesArePublic() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"));
        mockMvc.perform(get("/reset-password").param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"));
    }
}
