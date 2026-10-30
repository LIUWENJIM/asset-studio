package dev.liuwenjie.assetstudio.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把落盘目录以 /files/** 暴露出去，前端据此预览已上传素材与生成结果。
 * 模板封面走 classpath:/static/media/**，由 Spring Boot 默认静态资源处理接管。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storage;

    public WebMvcConfig(StorageProperties storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(storage.root()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}