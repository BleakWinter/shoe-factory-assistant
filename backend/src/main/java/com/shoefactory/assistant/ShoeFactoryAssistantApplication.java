package com.shoefactory.assistant;

import com.shoefactory.assistant.config.FileStorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
// 扫描 mapper 包，让 MyBatis Plus 能自动生成 Mapper Bean。
@MapperScan("com.shoefactory.assistant.mapper")
// 绑定 application.yml 里的 app.file-storage 配置。
@EnableConfigurationProperties(FileStorageProperties.class)
public class ShoeFactoryAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoeFactoryAssistantApplication.class, args);
    }
}
