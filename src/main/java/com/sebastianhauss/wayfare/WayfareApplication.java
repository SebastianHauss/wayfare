package com.sebastianhauss.wayfare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WayfareApplication {

    public static void main(String[] args) {
        SpringApplication.run(WayfareApplication.class, args);
    }
}
