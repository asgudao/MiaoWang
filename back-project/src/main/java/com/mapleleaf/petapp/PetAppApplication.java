package com.mapleleaf.petapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PetAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetAppApplication.class, args);
    }
}
