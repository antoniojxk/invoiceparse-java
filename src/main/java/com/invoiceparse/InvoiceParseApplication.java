package com.invoiceparse;

import com.invoiceparse.config.InvoiceParseProperties;
import com.invoiceparse.config.DocumentAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({InvoiceParseProperties.class, DocumentAiProperties.class})
public class InvoiceParseApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvoiceParseApplication.class, args);
    }
}
