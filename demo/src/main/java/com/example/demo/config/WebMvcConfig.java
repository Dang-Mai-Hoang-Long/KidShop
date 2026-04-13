package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthCsrfInterceptor authCsrfInterceptor;
    private final LockedAccountInterceptor lockedAccountInterceptor;

    public WebMvcConfig(AuthCsrfInterceptor authCsrfInterceptor, LockedAccountInterceptor lockedAccountInterceptor) {
        this.authCsrfInterceptor = authCsrfInterceptor;
        this.lockedAccountInterceptor = lockedAccountInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authCsrfInterceptor);
        registry.addInterceptor(lockedAccountInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path runtimeAssetDir = Paths.get("data/uploads/assets").toAbsolutePath();
        Path sourceAssetDir = Paths.get("src/main/resources/static/assets").toAbsolutePath();
        Path avatarUploadDir = Paths.get("data/uploads/users").toAbsolutePath();

        registry.addResourceHandler("/assets/**")
            .addResourceLocations(
                "file:" + runtimeAssetDir + "/",
                "file:" + sourceAssetDir + "/",
                "classpath:/static/assets/");

        registry.addResourceHandler("/assets/users/**")
                .addResourceLocations(
                        "file:" + avatarUploadDir + "/",
                "file:" + sourceAssetDir.resolve("users") + "/",
                        "classpath:/static/assets/users/");
    }
}