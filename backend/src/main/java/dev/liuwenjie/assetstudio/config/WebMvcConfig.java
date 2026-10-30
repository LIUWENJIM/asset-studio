package dev.liuwenjie.assetstudio.config;

import dev.liuwenjie.assetstudio.identity.CurrentUserIdArgumentResolver;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public WebMvcConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

    /**
     * 把上传素材与生成结果暴露成只读静态资源。
     * 库里存的相对路径形如 assets/{userId}/{assetId}.jpg，对外就是 /files/assets/...
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(storageProperties.root()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            // 目录不存在时 Path#toUri 不会补斜杠，缺斜杠会导致匹配不到文件
            location = location + "/";
        }
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}