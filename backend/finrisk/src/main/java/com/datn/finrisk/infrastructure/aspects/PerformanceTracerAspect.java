package com.datn.finrisk.infrastructure.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceTracerAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTracerAspect.class);

    @Around("""
        @within(org.springframework.stereotype.Service)
        || @within(org.springframework.stereotype.Controller)
        || @within(org.springframework.web.bind.annotation.RestController)
        || @within(org.springframework.stereotype.Repository)
    """)
    public Object profileAllSpringBeans(ProceedingJoinPoint pjp) throws Throwable {

        long start = System.currentTimeMillis();

        Object result = pjp.proceed();

        long time = System.currentTimeMillis() - start;

        logger.info("[TRACE-JAVA] {}.{}() executed in {} ms",
                pjp.getSignature().getDeclaringTypeName(),
                pjp.getSignature().getName(),
                time);

        return result;
    }
}