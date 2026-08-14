package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZaloAuthFilterTest {

    @Mock TelegramUserRepository userRepo;

    private ZaloAuthFilter filter(String appId, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new ZaloAuthFilter(userRepo, env, appId, "");
    }

    @Test
    void devTokenUpsertsAndAttachesUser() throws Exception {
        // dev profile + app-id set → the "dev:<id>" stub token resolves a user.
        when(userRepo.findById(9000000001L)).thenReturn(Optional.empty());
        when(userRepo.save(any(TelegramUser.class))).thenAnswer(inv -> inv.getArgument(0));
        ZaloAuthFilter f = filter("123456", "dev");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(ZaloAuthFilter.HEADER_ACCESS_TOKEN, "dev:9000000001");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, res, chain);

        verify(userRepo).save(any(TelegramUser.class)); // first-time upsert
        Object attached = req.getAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER);
        assertThat(attached).isInstanceOf(TelegramUser.class);
        assertThat(((TelegramUser) attached).getId()).isEqualTo(9000000001L);
    }

    @Test
    void disabledWhenNoAppIdIgnoresToken() throws Exception {
        ZaloAuthFilter f = filter("", "dev"); // no app-id → disabled
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(ZaloAuthFilter.HEADER_ACCESS_TOKEN, "dev:9000000001");
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, new MockHttpServletResponse(), chain);

        verify(userRepo, never()).save(any());
        assertThat(req.getAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER)).isNull();
    }

    @Test
    void noHeaderIsNoOp() throws Exception {
        ZaloAuthFilter f = filter("123456", "dev");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, new MockHttpServletResponse(), chain);

        verify(userRepo, never()).findById(any());
        assertThat(req.getAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER)).isNull();
    }
}
