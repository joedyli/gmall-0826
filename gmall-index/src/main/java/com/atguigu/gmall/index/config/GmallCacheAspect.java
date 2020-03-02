package com.atguigu.gmall.index.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class GmallCacheAspect {

    @Around("@annotation(com.atguigu.gmall.index.config.GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        // 拦截前代码块

        // 执行目标方法
        Object result = joinPoint.proceed(joinPoint.getArgs());

        // 拦截后代码块


        return result;
    }
}
