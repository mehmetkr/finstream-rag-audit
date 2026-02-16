package com.finstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinStreamApplication.class, args);
    }
}
