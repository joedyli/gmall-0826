package com.atguigu.gmall.index.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {

    /**
     * 缓存的前缀
     * @return
     */
    String prefix() default "";

    /**
     * 设置缓存的有效时间
     * 单位：分钟
     * @return
     */
    int timeout() default 5;

    /**
     * 防止雪崩设置的随机值范围
     * @return
     */
    int random() default 5;

    /**
     * 防止击穿，分布式锁的key
     * @return
     */
    String lock() default "lock";
}
