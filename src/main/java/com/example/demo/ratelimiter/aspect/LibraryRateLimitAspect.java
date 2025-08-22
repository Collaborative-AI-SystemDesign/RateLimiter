package com.example.demo.ratelimiter.aspect;

import com.example.demo.ratelimiter.annotation.LibraryRateLimit;
import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 라이브러리 기반 Rate Limit AOP Aspect
 * 
 * 🎯 특징:
 * - 기존 수동 구현 대비 훨씬 간단한 로직
 * - 라이브러리별 특화된 기능 활용
 * - 검증된 알고리즘 사용으로 안정성 보장
 * 
 * 📚 수동 구현 Aspect와의 차이점:
 * - 복잡한 설정 생성 로직 불필요
 * - 라이브러리의 고급 기능 활용 가능
 * - 에러 처리 간소화
 */
@Aspect
@Component
public class LibraryRateLimitAspect {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * @LibraryRateLimit 어노테이션이 적용된 메서드에 대해 Rate Limiting 적용
     */
    @Around("@annotation(libraryRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, LibraryRateLimit libraryRateLimit) throws Throwable {
        // Rate Limit 키 생성
        String key = generateKey(libraryRateLimit);
        
        // 라이브러리별 Rate Limiter 선택 및 처리
        RateLimitResult result = processRateLimit(libraryRateLimit, key);
        
        // Rate Limit 검사 결과 처리
        if (!result.isAllowed()) {
            long retryAfterSeconds = Math.max(1, (result.getResetTime() - System.currentTimeMillis()) / 1000);
            String message = String.format("🚫 %s (Library: %s, Retry after %d seconds)", 
                libraryRateLimit.message(), 
                libraryRateLimit.library().getDescription(),
                retryAfterSeconds);
            
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
        
        // 정상 처리
        return joinPoint.proceed();
    }
    
    /**
     * 라이브러리별 Rate Limit 처리
     */
    private RateLimitResult processRateLimit(LibraryRateLimit libraryRateLimit, String key) {
        try {
            RateLimiter rateLimiter = applicationContext.getBean(
                libraryRateLimit.library().getBeanName(), RateLimiter.class);
            
            // 기본 tryAcquire 사용 (라이브러리에서 알아서 처리)
            return rateLimiter.tryAcquire(key);
            
        } catch (Exception e) {
            // 라이브러리별 특화 기능 사용 시도
            return tryLibrarySpecificFeatures(libraryRateLimit, key, e);
        }
    }
    
    /**
     * 라이브러리별 특화 기능 사용
     */
    private RateLimitResult tryLibrarySpecificFeatures(LibraryRateLimit libraryRateLimit, String key, Exception originalException) {
        try {
            switch (libraryRateLimit.library()) {
                case BUCKET4J:
                    return tryBucket4jFeatures(key, libraryRateLimit);
                case RESILIENCE4J:
                    return tryResilience4jFeatures(key, libraryRateLimit);
                case GUAVA:
                    return tryGuavaFeatures(key, libraryRateLimit);
                default:
                    throw originalException;
            }
        } catch (Exception e) {
            // 모든 시도 실패 시 허용 (Fail-Open)
            return RateLimitResult.allowed(
                (long) libraryRateLimit.limit(),
                System.currentTimeMillis() + libraryRateLimit.periodSeconds() * 1000,
                "FALLBACK",
                "Library failed, allowing request (fail-open)"
            );
        }
    }
    
    /**
     * Bucket4j 특화 기능 사용
     */
    private RateLimitResult tryBucket4jFeatures(String key, LibraryRateLimit config) throws Exception {
        Object bucket4jLimiter = applicationContext.getBean("bucket4jRateLimiter");
        
        // Bucket4j의 커스텀 메서드 호출 시도
        Method customMethod = bucket4jLimiter.getClass().getMethod(
            "tryAcquire", String.class, int.class, Duration.class);
        
        return (RateLimitResult) customMethod.invoke(bucket4jLimiter, 
            key, (int) config.limit(), Duration.ofSeconds(config.periodSeconds()));
    }
    
    /**
     * Resilience4j 특화 기능 사용
     */
    private RateLimitResult tryResilience4jFeatures(String key, LibraryRateLimit config) throws Exception {
        Object resilience4jLimiter = applicationContext.getBean("resilience4jRateLimiter");
        
        // Resilience4j의 커스텀 메서드 호출 시도
        Method customMethod = resilience4jLimiter.getClass().getMethod(
            "tryAcquire", String.class, int.class, Duration.class);
        
        return (RateLimitResult) customMethod.invoke(resilience4jLimiter, 
            key, (int) config.limit(), Duration.ofSeconds(config.periodSeconds()));
    }
    
    /**
     * Guava 특화 기능 사용
     */
    private RateLimitResult tryGuavaFeatures(String key, LibraryRateLimit config) throws Exception {
        Object guavaLimiter = applicationContext.getBean("guavaRateLimiter");
        
        // Guava의 커스텀 메서드 호출 시도
        Method customMethod = guavaLimiter.getClass().getMethod(
            "tryAcquire", String.class, double.class);
        
        return (RateLimitResult) customMethod.invoke(guavaLimiter, key, config.limit());
    }
    
    /**
     * Rate Limit 키 생성 (수동 구현과 동일한 로직 재사용)
     */
    private String generateKey(LibraryRateLimit libraryRateLimit) {
        String prefix = "LIB_" + libraryRateLimit.library().name() + ":";
        
        switch (libraryRateLimit.keyType()) {
            case IP:
                return prefix + getClientIp();
            case USER:
                return prefix + "user:" + getUserId();
            case CUSTOM:
                return prefix + "custom:" + libraryRateLimit.customKey();
            default:
                return prefix + getClientIp();
        }
    }
    
    /**
     * 클라이언트 IP 주소 추출 (수동 구현과 동일)
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        }
        return "unknown";
    }
    
    /**
     * 사용자 ID 추출 (수동 구현과 동일)
     */
    private String getUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            String authorization = request.getHeader("Authorization");
            if (authorization != null && !authorization.isEmpty()) {
                return authorization.replaceAll("[^a-zA-Z0-9]", "");
            }
            
            Object userId = request.getSession().getAttribute("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        
        return getClientIp();
    }
} 