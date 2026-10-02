package com.junseo.common.security;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final Sessions sessions;

    public CurrentUserArgumentResolver(Sessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        long id;
        try {
            id = Long.parseLong(auth.getToken().getSubject());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        // A valid signature is not enough: the account may have been deleted, or the password changed since.
        if (!sessions.isValid(id, auth.getToken())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }
}
