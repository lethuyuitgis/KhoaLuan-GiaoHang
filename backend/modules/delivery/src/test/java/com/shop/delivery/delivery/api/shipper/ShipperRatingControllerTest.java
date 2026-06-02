package com.shop.delivery.delivery.api.shipper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.delivery.auth.api.CurrentUserArgumentResolver;
import com.shop.delivery.auth.api.WebMvcConfig;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.api.shipper.dto.RateCustomerRequest;
import com.shop.delivery.delivery.entity.ShipperRating;
import com.shop.delivery.delivery.service.ShipperRatingService;
import com.shop.delivery.shared.api.GlobalExceptionHandler;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShipperRatingController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({ShipperRatingController.class, GlobalExceptionHandler.class,
         CurrentUserArgumentResolver.class, WebMvcConfig.class})
class ShipperRatingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean ShipperRatingService service;
    @MockBean RoleResolver roleResolver;

    private TelegramUser shipperUser;

    @BeforeEach
    void setUp() {
        shipperUser = new TelegramUser();
        shipperUser.setId(2001L);
        when(roleResolver.hasRole(eq(2001L), eq(Role.SHIPPER))).thenReturn(true);
    }

    @Test
    void post_validRequest_returns200WithRating() throws Exception {
        UUID orderId = UUID.randomUUID();
        ShipperRating r = new ShipperRating();
        r.setId(77L);
        r.setOrderId(orderId);
        r.setShipperId(2001L);
        r.setCustomerId(1001L);
        r.setStars((short) 4);
        r.setComment("Khách dễ tìm");
        r.setCreatedAt(Instant.now());
        when(service.rateCustomer(eq(orderId), eq(2001L), eq(4), any())).thenReturn(r);

        RateCustomerRequest body = new RateCustomerRequest(4, "Khách dễ tìm");
        mvc.perform(post("/api/shipper/orders/{id}/rate-customer", orderId)
                .requestAttr("currentUser", shipperUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratingId").value(77))
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.stars").value(4))
            .andExpect(jsonPath("$.comment").value("Khách dễ tìm"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.customerId").doesNotExist())   // confirm PII NOT leaked
            .andExpect(jsonPath("$.shipperId").doesNotExist());
    }

    @Test
    void post_starsOutOfRange_returns400() throws Exception {
        mvc.perform(post("/api/shipper/orders/{id}/rate-customer", UUID.randomUUID())
                .requestAttr("currentUser", shipperUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 0}
                    """))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/shipper/orders/{id}/rate-customer", UUID.randomUUID())
                .requestAttr("currentUser", shipperUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 6}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_notShipper_returns401() throws Exception {
        TelegramUser nonShipper = new TelegramUser();
        nonShipper.setId(9999L);
        when(roleResolver.hasRole(eq(9999L), eq(Role.SHIPPER))).thenReturn(false);

        mvc.perform(post("/api/shipper/orders/{id}/rate-customer", UUID.randomUUID())
                .requestAttr("currentUser", nonShipper)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 5}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("NOT_SHIPPER"));
    }

    @Test
    void post_orderNotDelivered_returns422() throws Exception {
        when(service.rateCustomer(any(), any(), anyInt(), any()))
            .thenThrow(new BusinessRuleException("ORDER_NOT_RATEABLE",
                "Chỉ đánh giá được sau khi đơn đã giao"));

        mvc.perform(post("/api/shipper/orders/{id}/rate-customer", UUID.randomUUID())
                .requestAttr("currentUser", shipperUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 4}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_RATEABLE"));
    }

    @Test
    void post_alreadyRated_returns409() throws Exception {
        when(service.rateCustomer(any(), any(), anyInt(), any()))
            .thenThrow(new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi"));

        mvc.perform(post("/api/shipper/orders/{id}/rate-customer", UUID.randomUUID())
                .requestAttr("currentUser", shipperUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 5}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_RATED"));
    }
}
