package dev.liuwenjie.assetstudio;

import dev.liuwenjie.assetstudio.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class AssetStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetStudioApplication.class, args);
    }
}