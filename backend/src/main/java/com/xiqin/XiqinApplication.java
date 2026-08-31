package com.xiqin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableJpaRepositories(basePackages = "com.xiqin")
public class XiqinApplication {
    public static void main(String[] args) {
        SpringApplication.run(XiqinApplication.class, args);
    }
}
