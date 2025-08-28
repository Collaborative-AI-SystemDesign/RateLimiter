package com.example.demo.ratelimiter.algo.fixed;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import com.example.demo.ratelimiter.common.RedisRateLimiter;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis Fixed Window Counter Algorithm
 * 
 * 동작 원리:
 * - Redis의 INCR 명령어와 EXPIRE를 사용하여 고정 윈도우 구현
 * - 윈도우별로 독립적인 카운터 관리
 * - 윈도우 만료 시 자동으로 카운터 리셋
 * 
 * 장점:
 * - 구현이 매우 간단
 * - 메모리 효율적
 * - 분산 환경에서 일관된 제한
 * 
 * 단점:
 * - 윈도우 경계에서 버스트 트래픽 발생 가능
 * - 정확한 시간 기반 제어의 한계
 */
@Component
public class RedisFixedWindowRateLimiter implements RedisRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig defaultConfig;

    public RedisFixedWindowRateLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.defaultConfig = RateLimitConfig.forWindow(10, 60000); // 1분당 10개 요청
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        return tryAcquire(key, defaultConfig);
    }
    
    @Override
    public RateLimitResult getStatus(String key) {
        return getStatus(key, defaultConfig);
    }
    
    @Override
    public void reset(String key) {
        // 현재 윈도우 키를 삭제
        String redisKey = "fixed_window:" + key;
        long currentTime = System.currentTimeMillis();
        long window = currentTime / defaultConfig.getWindowSizeMs();
        String windowKey = redisKey + ":" + window;
        redisTemplate.delete(windowKey);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        String redisKey = "fixed_window:" + key;
        long currentTime = System.currentTimeMillis();
        
        // Lua 스크립트를 사용하지 않는 간단한 구현
        Long count = redisTemplate.opsForValue().increment(redisKey);

        // 키가 처음 생성되었다면, 만료 시간 설정 (윈도우 리셋 자동화)
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, Duration.ofMillis(config.getWindowSizeMs()));
        }

        // 제한 확인
        if (count != null && count <= config.getCapacity()) {
            return RateLimitResult.allowed(
                config.getCapacity() - count,
                getWindowEnd(currentTime, config.getWindowSizeMs()),
                "REDIS_FIXED_WINDOW",
                "Request counted in current window"
            );
        } else {
            return RateLimitResult.denied(
                0,
                getWindowEnd(currentTime, config.getWindowSizeMs()),
                "REDIS_FIXED_WINDOW",
                "Window limit exceeded"
            );
        }
    }
    
    /**
     * 상태 조회 (카운터 증가 없이)
     */
    public RateLimitResult getStatus(String key, RateLimitConfig config) {
        String redisKey = "fixed_window:" + key;
        long currentTime = System.currentTimeMillis();
        
        String countStr = redisTemplate.opsForValue().get(redisKey);
        long count = countStr != null ? Long.parseLong(countStr) : 0;
        
        return RateLimitResult.allowed(
            Math.max(0, config.getCapacity() - count),
            getWindowEnd(currentTime, config.getWindowSizeMs()),
            "REDIS_FIXED_WINDOW",
            "Current window status"
        );
    }
    
    /**
     * 현재 윈도우 종료 시간 계산
     */
    private long getWindowEnd(long currentTime, long windowSizeMs) {
        long window = currentTime / windowSizeMs;
        return (window + 1) * windowSizeMs;
    }
    
    /**
     * 레거시 메서드 - 기존 코드와의 호환성을 위해 유지
     */
    @Deprecated
    public boolean isAllowed(String key) {
        RateLimitResult result = tryAcquire(key);
        return result.isAllowed();
    }
}
