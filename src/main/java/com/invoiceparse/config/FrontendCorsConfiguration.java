package com.invoiceparse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class FrontendCorsConfiguration {
    @Bean
    FilterRegistrationBean<CorsFilter> frontendCors(
            @Value("${invoiceparse.frontend-origins:}") String configuredOrigins) {
        var origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        if (origins.contains("*")) {
            throw new IllegalArgumentException("Use explicit frontend origins, not a wildcard");
        }
        var source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration api = configuration(origins, List.of("GET", "POST", "OPTIONS"));
        source.registerCorsConfiguration("/api/**", api);
        source.registerCorsConfiguration("/actuator/health", configuration(origins, List.of("GET", "OPTIONS")));
        var registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private static CorsConfiguration configuration(List<String> origins, List<String> methods) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(methods);
        config.setAllowedHeaders(List.of("Content-Type"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        return config;
    }
}
