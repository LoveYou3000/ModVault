package com.modvault;

import com.modvault.config.ModVaultProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ModVault: Anthropic -&gt; OpenAI one-way protocol translation gateway.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ModVaultProperties.class)
@MapperScan("com.modvault.dal.mapper")
public class ModVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModVaultApplication.class, args);
    }
}
