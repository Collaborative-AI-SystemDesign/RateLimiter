package com.example.demo.ratelimiter.common;

/**
 * Rate Limiter 공통 인터페이스
 * 모든 Rate Limiting 알고리즘이 구현해야 하는 기본 메서드를 정의
 */
public interface RedisRateLimiter {
    
    /**
     * 요청이 허용되는지 확인하고 토큰/요청을 소비
     * @param key 고유 식별자 (IP, 사용자 ID 등)
     * @return 제한 결과
     */
    RateLimitResult tryAcquire(String key);
    
    /**
     * 현재 상태 조회 (토큰 소비 없이)
     * @param key 고유 식별자
     * @return 현재 상태 정보
     */
    RateLimitResult getStatus(String key);
    
    /**
     * 특정 키의 상태를 초기화
     * @param key 고유 식별자
     */
    default void reset(String key) {
        // 기본 구현은 비어있음 - 필요시 각 구현체에서 오버라이드
    }
} 