package com.ghoomo;

import com.ghoomo.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(AppProperties.class)
public class GhoomoApplication {
    public static void main(String[] args) {
        SpringApplication.run(GhoomoApplication.class, args);
    }
}
