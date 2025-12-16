package com.sku;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SKUTermApplication {

    public static void main(String[] args) {
        SpringApplication.run(SKUTermApplication.class, args);
    }
}
