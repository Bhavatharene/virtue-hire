package com.virtuehire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Paths;

@SpringBootApplication
public class VirtueHireApplication implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public static void main(String[] args) {
        SpringApplication.run(VirtueHireApplication.class, args);
    }

    // Serve uploaded files from backend folder
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
