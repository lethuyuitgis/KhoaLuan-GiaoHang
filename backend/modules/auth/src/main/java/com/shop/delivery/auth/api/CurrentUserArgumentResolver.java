package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.shared.exception.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && TelegramUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        TelegramUser user = req != null
            ? (TelegramUser) req.getAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER)
            : null;
        if (user == null) {
            throw new AuthenticationException("UNAUTHENTICATED", "Cần xác thực Telegram để truy cập");
        }
        return user;
    }
}
