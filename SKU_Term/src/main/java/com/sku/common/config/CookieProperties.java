package com.sku.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.cookie")
public class CookieProperties {

    private boolean secure;
    private String sameSite = "Lax";
    private String path = "/";

    /**
     * 비워두면 host-only 쿠키로 동작.
     * 특정 도메인을 꼭 써야 할 때만 사용(예: .example.com).
     */
    private String domain;

    private long accessMaxAgeSeconds = 3600;
    private long refreshMaxAgeSeconds = 21600;
}
