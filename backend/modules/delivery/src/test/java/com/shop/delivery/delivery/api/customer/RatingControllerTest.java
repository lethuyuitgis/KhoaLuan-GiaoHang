package com.shop.delivery.delivery.api.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.delivery.auth.api.CurrentUserArgumentResolver;
import com.shop.delivery.auth.api.WebMvcConfig;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.delivery.api.customer.dto.RateOrderRequest;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.service.RatingService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RatingController.class,
            excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import({RatingController.class, GlobalExceptionHandler.class, CurrentUserArgumentResolver.class, WebMvcConfig.class})
class RatingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean RatingService ratingService;

    private TelegramUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new TelegramUser();
        testUser.setId(1001L);
    }

    @Test
    void post_validRequest_returns200WithRating() throws Exception {
        UUID orderId = UUID.randomUUID();
        Rating r = new Rating();
        r.setId(42L);
        r.setOrderId(orderId);
        r.setStars((short) 5);
        when(ratingService.rate(eq(orderId), eq(1001L), eq(5), any()))
            .thenReturn(r);

        RateOrderRequest body = new RateOrderRequest(5, "Tốt");
        mvc.perform(post("/api/orders/{id}/rating", orderId)
                .requestAttr("currentUser", testUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.stars").value(5));
    }

    @Test
    void post_starsOutOfRange_returns400() throws Exception {
        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .requestAttr("currentUser", testUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 0, "comment": "x"}
                    """))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .requestAttr("currentUser", testUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 6, "comment": "x"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_orderNotDelivered_returns422() throws Exception {
        when(ratingService.rate(any(), any(), anyInt(), any()))
            .thenThrow(new BusinessRuleException("ORDER_NOT_RATEABLE", "Chỉ đánh giá được sau khi đơn đã giao"));

        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .requestAttr("currentUser", testUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 4}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_RATEABLE"));
    }

    @Test
    void post_alreadyRated_returns409() throws Exception {
        when(ratingService.rate(any(), any(), anyInt(), any()))
            .thenThrow(new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi"));

        mvc.perform(post("/api/orders/{id}/rating", UUID.randomUUID())
                .requestAttr("currentUser", testUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"stars": 5}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_RATED"));
    }
}
