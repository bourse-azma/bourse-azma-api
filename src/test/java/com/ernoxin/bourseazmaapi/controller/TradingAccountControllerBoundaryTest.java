package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.exception.GlobalExceptionHandler;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import com.ernoxin.bourseazmaapi.service.TradingAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradingAccountControllerBoundaryTest {

    private TradingAccountService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TradingAccountService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TradingAccountController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsStructurallyInvalidOrderBeforeCallingTradingService() throws Exception {
        authenticate(77L);

        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "side":"BUY",
                                  "orderType":"NORMAL",
                                  "priceType":"CUSTOM",
                                  "symbol":"TEST",
                                  "instrumentCode":"IRO1TEST0001",
                                  "quantity":0,
                                  "price":100,
                                  "livePrice":100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.category").value("اعتبارسنجی"))
                .andExpect(jsonPath("$.result.errors.quantity").exists());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownEnumWithStableApiErrorInsteadOfReturning500() throws Exception {
        authenticate(77L);

        mockMvc.perform(post("/api/v1/trading/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "side":"PURCHASE",
                                  "orderType":"NORMAL",
                                  "priceType":"CUSTOM",
                                  "symbol":"TEST",
                                  "instrumentCode":"IRO1TEST0001",
                                  "quantity":1,
                                  "price":100,
                                  "livePrice":100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.errors.body").exists());

        verifyNoInteractions(service);
    }

    @Test
    void missingAuthenticationIsMappedTo401AtControllerBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/trading/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.result.category").value("احراز هویت"));

        verifyNoInteractions(service);
    }

    @Test
    void authenticatedIdentityIsUsedInsteadOfAnyClientSuppliedUserId() throws Exception {
        authenticate(77L);
        when(service.cancelOrder(77L, 91L)).thenReturn(null);

        mockMvc.perform(post("/api/v1/trading/orders/91/cancel"))
                .andExpect(status().isOk());

        verify(service).cancelOrder(77L, 91L);
    }

    private void authenticate(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("test-user");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
