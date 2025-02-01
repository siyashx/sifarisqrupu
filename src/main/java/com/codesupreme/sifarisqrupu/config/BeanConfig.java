package com.codesupreme.sifarisqrupu.config;

import io.micrometer.common.lang.NonNullApi;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BeanConfig {

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NotNull CorsRegistry registry) {
                registry.addMapping("/**") // 🔥 Tüm API endpointlerini kapsar
                        .allowedOrigins("*") // ✅ Tüm domainlere izin ver
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 🔥 Tüm HTTP metodlarını aç
                        .allowedHeaders("*") // 🔥 Tüm header'lara izin ver
                        .allowCredentials(true); // ✅ Kimlik doğrulamalı istekleri destekle
            }
        };
    }
}
