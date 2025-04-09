package com.collegeshowdown.poker_project;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        try {
            SpringApplication.run(Application.class, args);
        } catch (Throwable t) {
            System.out.println("FAILURE !!!");
            t.printStackTrace();
        }
    }

    @Bean
    public CommandLineRunner keepAlive() {
        return args -> {
        };
    }
}
