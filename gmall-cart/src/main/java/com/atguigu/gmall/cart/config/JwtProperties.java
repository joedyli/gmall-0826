package com.atguigu.gmall.cart.config;

import com.atguigu.core.utils.RsaUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.security.PublicKey;

@Data
@Slf4j
@ConfigurationProperties("auth.jwt")
public class JwtProperties {

    private String publicKeyPath;
    private String cookieName;
    private String userKeyName;
    private Integer expireTime;

    private PublicKey publicKey;

    @PostConstruct
    public void init(){
        try {
            // 存在就读取公私钥
            this.publicKey = RsaUtils.getPublicKey(publicKeyPath);
        } catch (Exception e) {
            log.error("公钥读取失败，请检查你的公钥是否正确配置！");
        }
    }
}
