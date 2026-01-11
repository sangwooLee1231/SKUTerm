package com.sku;

import com.sku.common.config.CookieProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;



@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(CookieProperties.class)
public class SKUTermApplication {

    public static void main(String[] args) {
        SpringApplication.run(SKUTermApplication.class, args);
    }
}
