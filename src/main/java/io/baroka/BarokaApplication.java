package io.baroka;

import io.baroka.config.MessagingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@EnableConfigurationProperties({
        MessagingConfig.class
})
@ComponentScan(basePackages = "io.baroka.*")
@SpringBootApplication
public class BarokaApplication {
    public static void main(String[] args) {
        SpringApplication.run(BarokaApplication.class, args);
    }

}
