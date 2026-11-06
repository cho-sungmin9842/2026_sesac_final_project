package com.moviepick.backend.appinfo;

import com.moviepick.backend.config.AppVersionProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 프론트(헤더/관리자 사이드바)가 표시할 앱 버전을 내려줍니다. 로그인 전에도 호출할 수 있어 인증 헤더는 받지 않습니다.
@RestController
@RequestMapping("/api/app-version")
public class AppVersionController {

    private final AppVersionProperties appVersionProperties;

    public AppVersionController(AppVersionProperties appVersionProperties) {
        this.appVersionProperties = appVersionProperties;
    }

    @GetMapping
    public Map<String, String> get() {
        return Map.of("version", appVersionProperties.version());
    }
}
