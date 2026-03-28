package com.checkout.nexus.lesimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeSimulatorApplication.class, args);
    }
}
