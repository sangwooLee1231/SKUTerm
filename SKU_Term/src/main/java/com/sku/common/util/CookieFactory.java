package com.sku.common.util;

import com.sku.common.config.CookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CookieFactory {

    private final CookieProperties props;

    public ResponseCookie accessToken(String value) {
        return base("accessToken", value)
                .httpOnly(true)
                .maxAge(props.getAccessMaxAgeSeconds())
                .build();
    }

    public ResponseCookie refreshToken(String value) {
        return base("refreshToken", value)
                .httpOnly(true)
                .maxAge(props.getRefreshMaxAgeSeconds())
                .build();
    }

    public ResponseCookie clearAccessToken() {
        return base("accessToken", "")
                .httpOnly(true)
                .maxAge(0)
                .build();
    }

    public ResponseCookie clearRefreshToken() {
        return base("refreshToken", "")
                .httpOnly(true)
                .maxAge(0)
                .build();
    }


    public ResponseCookie clearQueueToken() {
        return base("queueToken", "")
                .httpOnly(false)
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
                .secure(props.isSecure())
                .path(props.getPath())
                .sameSite(props.getSameSite());

        if (StringUtils.hasText(props.getDomain())) {
            b.domain(props.getDomain());
        }
        return b;
    }
}
