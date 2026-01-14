package org.example.uno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.example.uno", "org.example.common"})
public class UnoApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnoApplication.class, args);
    }

}
