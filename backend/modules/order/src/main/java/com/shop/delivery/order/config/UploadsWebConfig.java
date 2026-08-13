package com.shop.delivery.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serve ảnh đã upload dưới /api/files/** (đi qua nginx cùng đường /api/ nên
 * miniapp / webadmin dùng thẳng đường dẫn tương đối trả về từ upload API).
 */
@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    private final String uploadsDir;

    public UploadsWebConfig(@Value("${app.uploads-dir:uploads}") String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/files/**")
            .addResourceLocations(Path.of(uploadsDir).toAbsolutePath().toUri().toString())
            .setCachePeriod(3600);
    }
}
