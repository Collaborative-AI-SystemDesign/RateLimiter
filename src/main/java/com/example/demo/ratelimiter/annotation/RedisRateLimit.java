package com.example.demo.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 기반 Rate Limit 어노테이션
 * 
 * 메서드에 적용하여 Redis를 사용한 분산 Rate Limiting을 적용할 수 있습니다.
 * 여러 인스턴스 간 일관된 제한을 제공하며, Lua 스크립트로 원자적 연산을 보장합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisRateLimit {
    
    /**
     * 사용할 Redis 기반 Rate Limiting 알고리즘
     */
    RedisAlgorithmType algorithm() default RedisAlgorithmType.REDIS_TOKEN_BUCKET;
    
    /**
     * Rate Limit 키 생성 방식
     * IP: 클라이언트 IP 주소 기반
     * USER: 사용자 ID 기반 (인증된 사용자)
     * CUSTOM: 커스텀 키 사용
     */
    KeyType keyType() default KeyType.IP;
    
    /**
     * 커스텀 키 (keyType이 CUSTOM일 때 사용)
     * SpEL 표현식 지원
     */
    String customKey() default "";
    
    /**
     * 최대 허용 요청 수 (용량)
     */
    int limit() default 10;
    
    /**
     * 시간 윈도우 (초)
     * Window 기반 알고리즘에서 사용
     */
    int windowSeconds() default 60;
    
    /**
     * 토큰 보충 속도 (초당 토큰 수)
     * Token Bucket과 Leaky Bucket에서 사용
     */
    int refillRate() default 1;
    
    /**
     * Rate Limit 초과 시 반환할 메시지
     */
    String message() default "Rate limit exceeded. Please try again later.";
    
    /**
     * Redis 기반 Rate Limiting 알고리즘 타입
     */
    enum RedisAlgorithmType {
        REDIS_TOKEN_BUCKET("redisTokenBucketLimiter"),
        REDIS_LEAKY_BUCKET("redisLeakyBucketLimiter"),
        REDIS_FIXED_WINDOW("redisFixedWindowRateLimiter"),
        REDIS_SLIDING_WINDOW_LOG("redisSlidingWindowLogRateLimiter"),
        REDIS_SLIDING_WINDOW_COUNTER("redisSlidingWindowCounterLimiter");
        
        private final String beanName;
        
        RedisAlgorithmType(String beanName) {
            this.beanName = beanName;
        }
        
        public String getBeanName() {
            return beanName;
        }
    }
    
    /**
     * Rate Limit 키 생성 방식
     */
    enum KeyType {
        IP,        // IP 주소 기반
        USER,      // 사용자 ID 기반
        CUSTOM     // 커스텀 키
    }
}

