package com.virtuehire.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register interceptor but exclude public endpoints and auth endpoints
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/public/**",
                        "/api/hrs/login",
                        "/api/hrs/register",
                        "/api/hrs/verify-email",
                        "/api/candidates/login",
                        "/api/candidates/register",
                        "/api/candidates/verify-otp",
                        "/api/candidates/verify-email",
                        "/api/candidates/resend-otp",
                        "/api/candidates/forgot-password",
                        "/api/candidates/reset-password",
                        "/api/candidates/file/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://admin.virtuehire.in", "http://localhost:3000") // React app URL
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
