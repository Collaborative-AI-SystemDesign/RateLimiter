package com.example.demo.ratelimiter.algo.bucket;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import com.example.demo.ratelimiter.common.RedisRateLimiter;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Redis Token Bucket Algorithm
 * 
 * 동작 원리:
 * - Redis에서 Lua 스크립트를 사용하여 원자적 연산 보장
 * - 고정된 용량의 버킷에 일정한 속도로 토큰을 추가
 * - 요청 시 토큰을 소비하며, 토큰이 없으면 요청 거부
 * - 분산 환경에서 정확한 rate limiting 제공
 * 
 * 장점:
 * - 분산 환경에서 일관된 rate limiting
 * - 버스트 트래픽을 허용하면서도 장기적으로 평균 속도를 제한
 * - Redis의 원자적 연산으로 정확성 보장
 * 
 * 단점:
 * - 네트워크 지연으로 인한 성능 오버헤드
 * - Redis 의존성
 */
@Component
public class RedisTokenBucketLimiter implements RedisRateLimiter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig defaultConfig;
    private final RedisScript<Long> tokenBucketScript;
    
    // Lua 스크립트 - 원자적 토큰 소비 로직
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        
        -- Redis에서 현재 토큰 수와 마지막 보충 시간 가져오기
        local bucket_data = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket_data[1]) or capacity
        local last_refill = tonumber(bucket_data[2]) or current_time
        
        -- 경과 시간 계산 및 토큰 보충
        local elapsed = math.max(0, current_time - last_refill)
        local tokens_to_add = math.floor(elapsed / 1000) * refill_rate
        tokens = math.min(capacity, tokens + tokens_to_add)
        
        -- 토큰이 있으면 소비하고 1 반환, 없으면 0 반환
        if tokens > 0 then
            tokens = tokens - 1
            -- 상태 저장 (토큰 수, 마지막 보충 시간)
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', current_time)
            -- TTL 설정 (메모리 정리용)
            redis.call('EXPIRE', key, 3600)
            return tokens + 1  -- 소비하기 전 토큰 수 반환 (remaining 계산용)
        else
            -- 토큰이 없으면 상태만 업데이트
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', current_time)
            redis.call('EXPIRE', key, 3600)
            return 0
        end
        """;
    
    public RedisTokenBucketLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.defaultConfig = RateLimitConfig.forTokenBucket(10, 1); // 10개 토큰, 초당 1개 보충
        this.tokenBucketScript = RedisScript.of(LUA_SCRIPT, Long.class);
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
        redisTemplate.delete(key);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        String redisKey = "token_bucket:" + key;
        long currentTime = System.currentTimeMillis();
        
        Long result = redisTemplate.execute(
            tokenBucketScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getRefillRate()),
            String.valueOf(currentTime)
        );
        
        if (result != null && result > 0) {
            return RateLimitResult.allowed(
                result - 1,  // 소비 후 남은 토큰 수
                getNextRefillTime(currentTime),
                "REDIS_TOKEN_BUCKET",
                "Token consumed successfully"
            );
        } else {
            return RateLimitResult.denied(
                0,
                getNextRefillTime(currentTime),
                "REDIS_TOKEN_BUCKET",
                "No tokens available"
            );
        }
    }
    
    /**
     * 상태 조회 (토큰 소비 없이)
     */
    public RateLimitResult getStatus(String key, RateLimitConfig config) {
        String redisKey = "token_bucket:" + key;
        
        // 현재 상태만 조회하는 Lua 스크립트
        String statusScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local bucket_data = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket_data[1]) or capacity
            local last_refill = tonumber(bucket_data[2]) or current_time
            
            -- 토큰 보충 계산 (소비하지 않음)
            local elapsed = math.max(0, current_time - last_refill)
            local tokens_to_add = math.floor(elapsed / 1000) * refill_rate
            tokens = math.min(capacity, tokens + tokens_to_add)
            
            return tokens
            """;
        
        RedisScript<Long> statusRedisScript = RedisScript.of(statusScript, Long.class);
        long currentTime = System.currentTimeMillis();
        
        Long tokens = redisTemplate.execute(
            statusRedisScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getRefillRate()),
            String.valueOf(currentTime)
        );
        
        return RateLimitResult.allowed(
            tokens != null ? tokens : config.getCapacity(),
            getNextRefillTime(currentTime),
            "REDIS_TOKEN_BUCKET",
            "Current status"
        );
    }
    
    /**
     * 다음 토큰 보충 시간 계산
     */
    private long getNextRefillTime(long currentTime) {
        return currentTime + 1000; // 1초 후
    }
}
