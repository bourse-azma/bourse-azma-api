package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.exception.GlobalExceptionHandler;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import com.ernoxin.bourseazmaapi.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletControllerBoundaryTest {

    private WalletService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WalletService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new WalletController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        authenticate();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsDescriptionLongerThanDatabaseColumnBeforeStartingWalletTransaction() throws Exception {
        String description = "x".repeat(256);

        mockMvc.perform(post("/api/v1/wallet/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADD","value":100,"description":"%s"}
                                """.formatted(description)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.errors.description").exists());

        verifyNoInteractions(service);
    }

    private void authenticate() {
        User user = new User();
        user.setId(7L);
        user.setUsername("wallet-user");
        user.setFirstName("Wallet");
        user.setLastName("User");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
