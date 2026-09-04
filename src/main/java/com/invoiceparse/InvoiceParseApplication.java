package com.invoiceparse;

import com.invoiceparse.config.InvoiceParseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InvoiceParseProperties.class)
public class InvoiceParseApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvoiceParseApplication.class, args);
    }
}
