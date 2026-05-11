package com.shoefactory.assistant;

import com.shoefactory.assistant.config.FileStorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.shoefactory.assistant.mapper")
@EnableConfigurationProperties(FileStorageProperties.class)
public class ShoeFactoryAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoeFactoryAssistantApplication.class, args);
    }
}
