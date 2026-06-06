package com.chuamgwei.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域 (CORS) 配置类
 * 允许前端独立运行在其他端口进行开发联调
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的来源 (在生产环境中可替换为具体的域名，开发阶段允许所有)
        config.addAllowedOriginPattern("*");
        // 允许的请求头
        config.addAllowedHeader("*");
        // 允许的 HTTP 方法 (GET, POST, PUT, DELETE 等)
        config.addAllowedMethod("*");
        // 是否允许携带 Cookie
        config.setAllowCredentials(true);
        // 预检请求的最大缓存时间 (3600秒 = 1小时)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有接口路径生效
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
