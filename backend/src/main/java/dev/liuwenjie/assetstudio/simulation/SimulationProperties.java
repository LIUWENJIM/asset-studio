package dev.liuwenjie.assetstudio.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 模拟器的默认值，来自 application.yml 的 simulation.*，可在容器里用环境变量覆盖。 */
@ConfigurationProperties(prefix = "simulation")
public record SimulationProperties(
        long assetCheckMs,
        long generatingMs,
        long finalizingMs,
        String failStage,
        int poolSize,
        int queueCapacity,
        boolean devApiEnabled) {
}