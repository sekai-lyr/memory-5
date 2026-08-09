package com.sekai.song.config;

import com.sekai.song.util.UploadPathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadResourceConfig.class);
    private static final Path UPLOAD_ROOT = UploadPathUtil.uploadRoot();
    private static final Path EXTERNAL_UPLOAD_ROOT = externalUploadRoot();

    private final LicenseAccessInterceptor licenseAccessInterceptor;

    @Autowired
    public UploadResourceConfig(LicenseAccessInterceptor licenseAccessInterceptor) {
        this.licenseAccessInterceptor = licenseAccessInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(UPLOAD_ROOT.resolve("audio"));
            Files.createDirectories(UPLOAD_ROOT.resolve("video"));
            Files.createDirectories(EXTERNAL_UPLOAD_ROOT.resolve("audio"));
            LOGGER.info("Serving uploaded files from {}", UPLOAD_ROOT);
            LOGGER.info("Serving external uploaded files from {}", EXTERNAL_UPLOAD_ROOT);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare upload directories", e);
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(UPLOAD_ROOT.toUri().toString());
        registry.addResourceHandler("/external-uploads/**")
                .addResourceLocations(EXTERNAL_UPLOAD_ROOT.toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(licenseAccessInterceptor)
                .addPathPatterns("/**");
    }

    private static Path externalUploadRoot() {
        String configured = System.getProperty("sekai.externalUploadRoot");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SEKAI_EXTERNAL_UPLOAD_ROOT");
        }
        if (configured == null || configured.isBlank()) {
            configured = "D:/SekaiSongExternalUploads/memory-5/uploads";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }
}
