package com.example.demo.ratelimiter.common;

import lombok.Builder;
import lombok.Getter;

/**
 * Rate Limiter 설정을 담는 클래스
 */
@Getter
@Builder
public class RateLimitConfig {
    
    private final int capacity;           // 최대 용량 (토큰 수, 윈도우 크기 등)
    private final int refillRate;         // 보충 속도 (초당 토큰 수)
    private final long windowSizeMs;      // 윈도우 크기 (밀리초)
    private final long timeoutMs;         // 타임아웃 (밀리초)
    
    /**
     * 기본 설정으로 생성
     */
    public static RateLimitConfig defaultConfig() {
        return RateLimitConfig.builder()
                .capacity(10)
                .refillRate(1)
                .windowSizeMs(60000)  // 1분
                .timeoutMs(1000)      // 1초
                .build();
    }
    
    /**
     * Token Bucket용 설정
     */
    public static RateLimitConfig forTokenBucket(int capacity, int refillRate) {
        return RateLimitConfig.builder()
                .capacity(capacity)
                .refillRate(refillRate)
                .windowSizeMs(1000)   // 1초
                .timeoutMs(0)
                .build();
    }
    
    /**
     * Window 기반 알고리즘용 설정
     */
    public static RateLimitConfig forWindow(int capacity, long windowSizeMs) {
        return RateLimitConfig.builder()
                .capacity(capacity)
                .refillRate(0)
                .windowSizeMs(windowSizeMs)
                .timeoutMs(0)
                .build();
    }
} 