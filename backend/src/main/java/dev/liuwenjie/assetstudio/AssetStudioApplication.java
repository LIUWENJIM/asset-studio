package dev.liuwenjie.assetstudio;

import dev.liuwenjie.assetstudio.config.StorageProperties;
import dev.liuwenjie.assetstudio.simulation.SimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, SimulationProperties.class})
public class AssetStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetStudioApplication.class, args);
    }
}