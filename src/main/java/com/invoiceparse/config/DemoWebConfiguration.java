package com.invoiceparse.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class DemoWebConfiguration {
    @Bean
    @ConditionalOnProperty(name = "invoiceparse.demo-access.enabled", havingValue = "true")
    FilterRegistrationBean<DemoSecurityHeadersFilter> demoSecurityHeaders() {
        var registration = new FilterRegistrationBean<>(new DemoSecurityHeadersFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
