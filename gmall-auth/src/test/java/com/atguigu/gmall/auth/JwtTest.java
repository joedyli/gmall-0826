package com.atguigu.gmall.auth;

import com.atguigu.core.utils.JwtUtils;
import com.atguigu.core.utils.RsaUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {
	private static final String pubKeyPath = "D:\\project-0826\\secret\\rsa.pub";

    private static final String priKeyPath = "D:\\project-0826\\secret\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @Before
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 2);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1ODM1NTEwNjJ9.kWoLnewwzoaF1wxFxM_1VzrLOd54v6AvZcGsG5LfnPF-OSThn8k9ptRZg2Xef98wJRfVM3gPsRYMu7pCwgg3OWoscsT3tNGc_-yIva5VtH8Q934pMgHN_quKdtRUSo00EgN55lj0FJwAG5TJj2I-Pnz-ZLSk9ZNh245r7MCkU08q7o3ZOvrw1reegph4jGdKLilaG5oFQfaNRS684UxE7X8cPvO7S66Tc03OuoPrr_o8eGDwsFPG8m2ray-7l1GWo_gLQyGs4QItIu5FGwSOBgWWKgDVfvad_pWNDYSExU6VFmy6gjslkQgIOMpFPkPGcKYFCmrYtVDufFclQ07tbA";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}