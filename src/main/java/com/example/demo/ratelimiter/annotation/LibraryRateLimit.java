package com.example.demo.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 라이브러리 기반 Rate Limit 어노테이션
 * Bucket4j, Resilience4j, Guava 라이브러리를 사용합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LibraryRateLimit {
    
    /**
     * 사용할 라이브러리 타입
     */
    LibraryType library() default LibraryType.BUCKET4J;
    
    /**
     * Rate Limit 키 생성 방식
     */
    KeyType keyType() default KeyType.IP;
    
    /**
     * 커스텀 키 (keyType이 CUSTOM일 때 사용)
     */
    String customKey() default "";
    
    /**
     * 최대 허용 요청 수 또는 속도
     */
    double limit() default 10.0;
    
    /**
     * 시간 단위 (초)
     * Bucket4j, Resilience4j에서 사용
     */
    long periodSeconds() default 60;
    
    /**
     * 타임아웃 (밀리초)
     * Guava에서 사용
     */
    long timeoutMs() default 100;
    
    /**
     * Rate Limit 초과 시 반환할 메시지
     */
    String message() default "Rate limit exceeded by library implementation.";
    
    /**
     * 라이브러리 타입
     */
    enum LibraryType {
        BUCKET4J("bucket4jRateLimiter", "검증된 Token Bucket 구현"),
        RESILIENCE4J("resilience4jRateLimiter", "Spring Boot 완벽 통합"),
        GUAVA("guavaRateLimiter", "Google의 간단한 구현");
        
        private final String beanName;
        private final String description;
        
        LibraryType(String beanName, String description) {
            this.beanName = beanName;
            this.description = description;
        }
        
        public String getBeanName() {
            return beanName;
        }
        
        public String getDescription() {
            return description;
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