package com.pdp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith
@SpringBootApplication(scanBasePackages = "com.pdp")
public class PdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdpApplication.class, args);
    }
}

