package com.warehouse.alertprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class WarehouseAlertProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseAlertProcessorApplication.class, args);
    }
}
