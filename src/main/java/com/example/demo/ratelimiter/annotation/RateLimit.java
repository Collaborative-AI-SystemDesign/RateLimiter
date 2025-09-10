package com.example.demo.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate Limit 어노테이션
 * 메서드에 적용하여 해당 메서드의 호출을 제한할 수 있습니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * 사용할 Rate Limiting 알고리즘
     */
    AlgorithmType algorithm() default AlgorithmType.TOKEN_BUCKET;
    
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
     * Rate Limiting 알고리즘 타입
     */
    enum AlgorithmType {
        TOKEN_BUCKET("tokenBucketLimiter"),
        LEAKY_BUCKET("leakyBucketLimiter"),
        FIXED_WINDOW("fixedWindowCounterLimiter"),
        SLIDING_WINDOW_LOG("slidingWindowLogLimiter"),
        SLIDING_WINDOW_COUNTER("slidingWindowCounterLimiter");
        
        private final String beanName;
        
        AlgorithmType(String beanName) {
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
        API,       // API 엔드포인트 기반
        CUSTOM     // 커스텀 키
    }
} 