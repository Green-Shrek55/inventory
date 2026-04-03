package com.kursach.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InventorySystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventorySystemApplication.class, args);
    }
}
