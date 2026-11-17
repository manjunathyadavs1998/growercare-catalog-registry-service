package com.growercare.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.growercare")
public class CatalogRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogRegistryApplication.class, args);
    }
}
