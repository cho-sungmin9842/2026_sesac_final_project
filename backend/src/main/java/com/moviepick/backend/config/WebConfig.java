package com.moviepick.backend.config;

import com.moviepick.backend.auth.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;
    private final UserService userService;
    private final UploadProperties uploadProperties;

    public WebConfig(AppProperties appProperties, UserService userService, UploadProperties uploadProperties) {
        this.appProperties = appProperties;
        this.userService = userService;
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(appProperties.allowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // 로그인한 사용자를 식별하는 X-User-Id 커스텀 헤더를 프리플라이트에서 허용해야 합니다.
                .allowedHeaders("Content-Type", "X-User-Id");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserActivityInterceptor(userService)).addPathPatterns("/api/**");
    }

    // 리뷰 사진 첨부 - ReviewService가 app.upload.dir 아래에 저장한 파일을 "/uploads/**" URL로 그대로
    // 내려줍니다. 절대 경로로 변환해둬야, 백엔드를 어느 작업 디렉터리에서 실행하든 같은 파일을 가리킵니다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteUploadDir = Paths.get(uploadProperties.dir()).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absoluteUploadDir + "/");
    }
}
