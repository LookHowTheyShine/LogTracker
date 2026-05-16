package com.warehouse.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WarehouseDashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarehouseDashboardApplication.class, args);
    }
}
