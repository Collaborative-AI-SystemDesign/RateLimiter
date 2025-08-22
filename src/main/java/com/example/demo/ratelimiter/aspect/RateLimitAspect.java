package com.example.demo.ratelimiter.aspect;

import com.example.demo.ratelimiter.annotation.RateLimit;
import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
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

/**
 * Rate Limit AOP Aspect
 * @RateLimit 어노테이션이 적용된 메서드의 호출을 가로채서 Rate Limiting을 적용합니다.
 */
@Aspect
@Component
public class RateLimitAspect {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * @RateLimit 어노테이션이 적용된 메서드에 대해 Rate Limiting 적용
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // Rate Limit 키 생성
        String key = generateKey(rateLimit);
        
        // 해당 알고리즘의 Rate Limiter 선택
        RateLimiter limiter = getRateLimiter(rateLimit.algorithm());
        
        // 설정에 따른 Rate Limiter 구성
        RateLimitResult result;
        if (needsCustomConfig(rateLimit)) {
            RateLimitConfig config = createConfig(rateLimit);
            result = tryAcquireWithConfig(limiter, key, config);
        } else {
            result = limiter.tryAcquire(key);
        }
        
        // Rate Limit 검사 결과 처리
        if (!result.isAllowed()) {
            long retryAfterSeconds = (result.getResetTime() - System.currentTimeMillis()) / 1000;
            String message = String.format("%s (Retry after %d seconds)", 
                rateLimit.message(), Math.max(1, retryAfterSeconds));
            
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
        
        // 정상 처리
        return joinPoint.proceed();
    }
    
    /**
     * Rate Limit 키 생성
     */
    private String generateKey(RateLimit rateLimit) {
        String prefix = rateLimit.algorithm().name() + ":";
        
        switch (rateLimit.keyType()) {
            case IP:
                return prefix + getClientIp();
            case USER:
                return prefix + "user:" + getUserId();
            case CUSTOM:
                return prefix + "custom:" + rateLimit.customKey();
            default:
                return prefix + getClientIp();
        }
    }
    
    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // X-Forwarded-For 헤더 확인 (프록시 환경)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            // X-Real-IP 헤더 확인
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            // 기본 Remote Address
            return request.getRemoteAddr();
        }
        return "unknown";
    }
    
    /**
     * 사용자 ID 추출 (현재는 간단한 구현)
     * 실제 환경에서는 JWT 토큰이나 세션에서 사용자 정보를 추출
     */
    private String getUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // Authorization 헤더에서 사용자 정보 추출 (간단한 예시)
            String authorization = request.getHeader("Authorization");
            if (authorization != null && !authorization.isEmpty()) {
                return authorization.replaceAll("[^a-zA-Z0-9]", ""); // 간단한 정리
            }
            
            // 세션에서 사용자 정보 추출
            Object userId = request.getSession().getAttribute("userId");
            if (userId != null) {
                return userId.toString();
            }
        }
        
        // 사용자 정보가 없으면 IP로 대체
        return getClientIp();
    }
    
    /**
     * 알고리즘에 해당하는 Rate Limiter 빈 조회
     */
    private RateLimiter getRateLimiter(RateLimit.AlgorithmType algorithm) {
        try {
            return applicationContext.getBean(algorithm.getBeanName(), RateLimiter.class);
        } catch (Exception e) {
            // 기본값으로 Token Bucket 사용
            return applicationContext.getBean("tokenBucketLimiter", RateLimiter.class);
        }
    }
    
    /**
     * 커스텀 설정이 필요한지 확인
     */
    private boolean needsCustomConfig(RateLimit rateLimit) {
        // 기본값과 다른 설정이 있는지 확인
        return rateLimit.limit() != 10 || 
               rateLimit.windowSeconds() != 60 || 
               rateLimit.refillRate() != 1;
    }
    
    /**
     * Rate Limit 설정 생성
     */
    private RateLimitConfig createConfig(RateLimit rateLimit) {
        switch (rateLimit.algorithm()) {
            case TOKEN_BUCKET:
            case LEAKY_BUCKET:
                return RateLimitConfig.forTokenBucket(rateLimit.limit(), rateLimit.refillRate());
            case FIXED_WINDOW:
            case SLIDING_WINDOW_LOG:
            case SLIDING_WINDOW_COUNTER:
                return RateLimitConfig.forWindow(rateLimit.limit(), rateLimit.windowSeconds() * 1000L);
            default:
                return RateLimitConfig.defaultConfig();
        }
    }
    
    /**
     * 커스텀 설정으로 Rate Limiter 호출
     * 리플렉션을 사용하여 설정을 전달
     */
    private RateLimitResult tryAcquireWithConfig(RateLimiter limiter, String key, RateLimitConfig config) {
        try {
            // tryAcquire(String key, RateLimitConfig config) 메서드가 있는지 확인
            Method method = limiter.getClass().getMethod("tryAcquire", String.class, RateLimitConfig.class);
            return (RateLimitResult) method.invoke(limiter, key, config);
        } catch (Exception e) {
            // 메서드가 없으면 기본 메서드 사용
            return limiter.tryAcquire(key);
        }
    }
} 