package com.atguigu.gmall.auth.config;

import com.atguigu.core.utils.RsaUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;

@Data
@Slf4j
@ConfigurationProperties("auth.jwt")
public class JwtProperties {

    private String privateKeyPath;
    private String publicKeyPath;
    private String secret;
    private String cookieName;
    private Integer exprieTime;

    private PublicKey publicKey;
    private PrivateKey privateKey;

    @PostConstruct
    public void init(){
        try {
            File priFile = new File(privateKeyPath);
            File pubFile = new File(publicKeyPath);
            // 判断公私钥文件是否存在，如果不存在，那么就初始化
            if (!priFile.exists() || !pubFile.exists()){
                RsaUtils.generateKey(publicKeyPath, privateKeyPath, secret);
            }
            // 存在就读取公私钥
            this.publicKey = RsaUtils.getPublicKey(publicKeyPath);
            this.privateKey = RsaUtils.getPrivateKey(privateKeyPath);
        } catch (Exception e) {
            log.error("公钥和私钥生成失败！");
        }
    }
}
