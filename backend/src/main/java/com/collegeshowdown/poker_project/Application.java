package com.collegeshowdown.poker_project;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        try {
            SpringApplication.run(Application.class, args);
        } catch (Throwable t) {
            // spring-boot-devtools (commented out in pom.xml) restarts by throwing
            // this out of main — exiting on it would kill every hot reload.
            if (t.getClass().getName().endsWith("SilentExitException")) throw t;
            System.out.println("FAILURE !!!");
            t.printStackTrace();
            // Without this the JVM exits 0 after a failed boot (bad DB creds,
            // weak JWT secret, ...), so the host sees a "clean" exit rather
            // than a crashed deploy.
            System.exit(1);
        }
    }

    @Bean
    public CommandLineRunner keepAlive() {
        return args -> {
        };
    }
}
