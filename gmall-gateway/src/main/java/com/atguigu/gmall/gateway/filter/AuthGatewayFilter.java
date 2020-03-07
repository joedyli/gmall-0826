package com.atguigu.gmall.gateway.filter;

import com.atguigu.core.utils.CookieUtils;
import com.atguigu.core.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties({JwtProperties.class})
public class AuthGatewayFilter implements GatewayFilter {

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // springWebMvc(servlet): httpServletRequest HttpServletResponse CorsFilter
        // springWebFlux(reactive)：这里就是webflux
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        // 1.从cookie中获取jwt类型的token
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        // 判断cookie是否为null
        if (CollectionUtils.isEmpty(cookies) || !cookies.containsKey(jwtProperties.getCookieName())) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
        HttpCookie cookie = cookies.getFirst(this.jwtProperties.getCookieName());

        // 2.判断是否为空
        if (cookie == null || StringUtils.isEmpty(cookie.getValue())){
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        try {
            // 3.解析jwt
            JwtUtils.getInfoFromToken(cookie.getValue(), this.jwtProperties.getPublicKey());
            // 放行
            return chain.filter(exchange);
        } catch (Exception e) {
            e.printStackTrace();
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }
}
