package com.atguigu.gmall.cart.interceptor;

import com.atguigu.core.utils.CookieUtils;
import com.atguigu.core.utils.JwtUtils;
import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.core.bean.UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties({JwtProperties.class})
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

//    public static Long USER_ID;  无法保证线程安全

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 目的：获取登录状态，传给后续业务逻辑：controller service dao
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.获取cookie中的信息：token userKey
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKeyName());

        // 2.判断userKey是否为空，如果为空需要制作一个放入cookie中
        if (StringUtils.isEmpty(userKey)) {
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.jwtProperties.getUserKeyName(), userKey, this.jwtProperties.getExpireTime());
        }

        // 3.判断token是否为空
        Long userId = null;
        if (StringUtils.isNotBlank(token)){
            // 4.解析token，正常解析获取userId
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
            userId = Long.valueOf(map.get("userId").toString());
        }

        // 5.把userId或者是userKey传递给后续的业务逻辑
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUserKey(userKey);
        THREAD_LOCAL.set(userInfo);

        return true;
    }

    /**
     * 这里封装一个get方法获取载荷数据
     * 如果把THREAD_LOCAL声明为公开，会不安全
     * @return
     */
    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // tomcat线程池，请求处理完成后，线程并没有结束
        THREAD_LOCAL.remove(); // 手动释放线程变量
    }
}
