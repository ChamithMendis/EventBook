package com.chamith.eventbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class EventBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventBookApplication.class, args);
    }
}
