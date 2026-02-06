package candi.demo;

import candi.runtime.CandiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CandiAutoConfiguration.class)
public class CandiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandiDemoApplication.class, args);
    }
}
