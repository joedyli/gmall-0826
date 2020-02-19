package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsFilterConfig {

    @Bean
    public CorsWebFilter corsWebFilter(){

        // 跨域请求配置对象
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:1000"); // 允许那些域名跨域访问，不要写*（代表所有域名，但不能携带cookie）
        configuration.addAllowedOrigin("http://127.0.0.1:1000");
        configuration.addAllowedMethod("*"); // 允许所有方法跨域访问
        configuration.setAllowCredentials(true); // 允许携带cookie
        configuration.addAllowedHeader("*"); // 允许所有头信息跨域访问

        // 拦截所有请求
        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(configurationSource);
    }
}
