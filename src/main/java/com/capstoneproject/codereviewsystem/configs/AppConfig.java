package com.capstoneproject.codereviewsystem.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class AppConfig implements WebMvcConfigurer {

    @Value("${app.storage.local.base-path:uploads}")
    private String localBasePath;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + localBasePath + "/avatars/");
    }
}