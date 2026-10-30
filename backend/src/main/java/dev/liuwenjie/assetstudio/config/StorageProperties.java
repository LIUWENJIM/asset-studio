package dev.liuwenjie.assetstudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上传素材与生成结果的落盘根目录。
 * 容器里通过 STORAGE_ROOT 指向挂载卷，本地默认 ./data。
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String root) {
}