package com.example.demo.ratelimiter.algo;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import com.example.demo.ratelimiter.common.RedisRateLimiter;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

/**
 * Redis Sliding Window Log Algorithm
 * 
 * 동작 원리:
 * - Redis Sorted Set을 사용하여 각 요청의 타임스탬프를 로그에 저장
 * - 윈도우 크기만큼의 과거 요청들을 추적
 * - 새 요청 시 오래된 로그를 제거하고 현재 요청 수 확인
 * - Lua 스크립트로 원자적 연산 보장
 * 
 * 장점:
 * - 정확한 제한이 가능 (윈도우 경계 문제 없음)
 * - 균등한 요청 분산 보장
 * - 버스트 트래픽을 효과적으로 제어
 * - 분산 환경에서 일관된 제한
 * 
 * 단점:
 * - 메모리 사용량이 많음 (모든 요청 타임스탬프 저장)
 * - 성능이 상대적으로 떨어짐
 * - 높은 트래픽에서 확장성 제한
 */
@Component
public class RedisSlidingWindowLogRateLimiter implements RedisRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig defaultConfig;
    private final RedisScript<Long> slidingWindowLogScript;

    // 개선된 Lua 스크립트 - 완전한 정리 보장
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window_size = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        local request_id = ARGV[4]
        
        -- 윈도우 시작 시간 계산  
        local window_start = current_time - window_size
        
        -- 1. 간단하게 오래된 요청 삭제 (양수일 때만)
        if window_start > 0 then
            redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
        end
        
        -- 2. 현재 윈도우 내의 요청 수 확인
        local count = redis.call('ZCARD', key)
        
        -- 3. 제한 확인
        if count < limit then
            -- 4. 현재 요청을 로그에 추가
            redis.call('ZADD', key, current_time, request_id)
            -- TTL 설정 (윈도우 크기 * 2 + 여유시간)
            redis.call('EXPIRE', key, math.ceil(window_size / 1000) * 2 + 60)
            return limit - count - 1  -- 남은 용량 반환
        else
            return -1  -- 거부됨
        end
        """;

    public RedisSlidingWindowLogRateLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.defaultConfig = RateLimitConfig.forWindow(10, 60000); // 1분당 10개 요청
        this.slidingWindowLogScript = RedisScript.of(LUA_SCRIPT, Long.class);
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
        String redisKey = "sliding_window_log:" + key;
        redisTemplate.delete(redisKey);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        String redisKey = "sliding_window_log:" + key;
        long currentTime = System.currentTimeMillis();
        String requestId = currentTime + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        Long result = redisTemplate.execute(
            slidingWindowLogScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getWindowSizeMs()),
            String.valueOf(currentTime),
            requestId
        );
        
        if (result != null && result >= 0) {
            return RateLimitResult.allowed(
                result,
                getNextResetTime(redisKey, currentTime, config.getWindowSizeMs()),
                "REDIS_SLIDING_WINDOW_LOG",
                "Request allowed, remaining: " + result
            );
        } else {
            return RateLimitResult.denied(
                0,
                getNextResetTime(redisKey, currentTime, config.getWindowSizeMs()),
                "REDIS_SLIDING_WINDOW_LOG",
                "Rate limit exceeded"
            );
        }
    }
    
    /**
     * 상태 조회 (요청 추가 없이)
     */
    public RateLimitResult getStatus(String key, RateLimitConfig config) {
        String redisKey = "sliding_window_log:" + key;
        long currentTime = System.currentTimeMillis();
        
        // 상태만 조회하는 Lua 스크립트
        String statusScript = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local window_start = current_time - window_size
            
            -- 간단하게 오래된 요청 삭제 (양수일 때만)
            if window_start > 0 then
                redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
            end
            
            -- 현재 요청 수 확인
            local count = redis.call('ZCARD', key)
            
            return math.max(0, limit - count)
            """;
        
        RedisScript<Long> statusRedisScript = RedisScript.of(statusScript, Long.class);
        
        Long remainingCapacity = redisTemplate.execute(
            statusRedisScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getWindowSizeMs()),
            String.valueOf(currentTime)
        );
        
        return RateLimitResult.allowed(
            remainingCapacity != null ? remainingCapacity : config.getCapacity(),
            getNextResetTime(redisKey, currentTime, config.getWindowSizeMs()),
            "REDIS_SLIDING_WINDOW_LOG",
            "Current sliding window status"
        );
    }
    
    /**
     * 다음 리셋 시간 계산
     * 가장 오래된 요청이 윈도우를 벗어나는 시간
     */
    private long getNextResetTime(String redisKey, long currentTime, long windowSizeMs) {
        // 가장 오래된 요청의 타임스탬프 가져오기
        Double oldestScore = redisTemplate.opsForZSet().range(redisKey, 0, 0)
            .stream()
            .findFirst()
            .map(member -> redisTemplate.opsForZSet().score(redisKey, member))
            .orElse(null);
        
        if (oldestScore != null) {
            return oldestScore.longValue() + windowSizeMs;
        } else {
            return currentTime + windowSizeMs;
        }
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
