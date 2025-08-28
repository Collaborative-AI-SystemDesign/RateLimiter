package com.example.demo.ratelimiter.algo.bucket;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import com.example.demo.ratelimiter.common.RedisRateLimiter;
import lombok.NoArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

/**
 * Redis Leaky Bucket Algorithm
 *
 * 동작 원리:
 * - Redis Sorted Set을 사용하여 요청들을 타임스탬프 순으로 저장
 * - 일정한 속도로 요청을 처리 (leak)하여 트래픽 평활화
 * - 버킷이 가득 차면 새로운 요청 거부
 * - Lua 스크립트로 원자적 연산 보장
 *
 * 장점:
 * - 분산 환경에서 일관된 트래픽 평활화
 * - 일정한 출력 속도를 보장하여 백엔드 시스템 보호
 * - Redis의 원자적 연산으로 정확성 보장
 *
 * 단점:
 * - 네트워크 지연으로 인한 성능 오버헤드
 * - 버스트 트래픽을 처리할 수 없음
 * - Redis 의존성
 */
@Component
public class RedisLeakyBucketLimiter implements RedisRateLimiter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig defaultConfig;
    private final RedisScript<Long> leakyBucketScript;
    
    // Lua 스크립트 - 원자적 leak 및 요청 추가 로직
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local leak_rate = tonumber(ARGV[2])  -- 초당 처리할 수 있는 요청 수
        local current_time = tonumber(ARGV[3])
        local request_id = ARGV[4]
        
        -- 마지막 leak 시간 가져오기
        local last_leak_key = key .. ':last_leak'
        local last_leak = tonumber(redis.call('GET', last_leak_key)) or 0
        
        -- 경과 시간에 따라 처리할 요청 수 계산
        local elapsed = math.max(0, current_time - last_leak)
        local requests_to_leak = math.floor(elapsed / 1000) * leak_rate
        
        -- 처리할 요청이 있으면 오래된 요청들을 제거 (leak)
        if requests_to_leak > 0 then
            -- 가장 오래된 요청들부터 제거
            redis.call('ZREMRANGEBYRANK', key, 0, requests_to_leak - 1)
            -- 마지막 leak 시간 업데이트
            redis.call('SET', last_leak_key, current_time)
            redis.call('EXPIRE', last_leak_key, 3600)
        end
        
        -- 현재 버킷의 요청 수 확인
        local current_size = redis.call('ZCARD', key)
        
        -- 버킷에 여유가 있으면 요청 추가
        if current_size < capacity then
            redis.call('ZADD', key, current_time, request_id)
            redis.call('EXPIRE', key, 3600)
            return capacity - current_size - 1  -- 추가 후 남은 용량
        else
            return -1  -- 버킷이 가득 참
        end
        """;
    
    public RedisLeakyBucketLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.defaultConfig = RateLimitConfig.forTokenBucket(10, 1); // 10개 용량, 초당 1개 처리
        this.leakyBucketScript = RedisScript.of(LUA_SCRIPT, Long.class);
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
        String redisKey = "leaky_bucket:" + key;
        String lastLeakKey = redisKey + ":last_leak";
        redisTemplate.delete(redisKey);
        redisTemplate.delete(lastLeakKey);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        String redisKey = "leaky_bucket:" + key;
        long currentTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        
        Long result = redisTemplate.execute(
            leakyBucketScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getRefillRate()), // leak rate로 사용
            String.valueOf(currentTime),
            requestId
        );
        
        if (result != null && result >= 0) {
            return RateLimitResult.allowed(
                result,
                getNextLeakTime(currentTime),
                "REDIS_LEAKY_BUCKET",
                "Request added to bucket"
            );
        } else {
            return RateLimitResult.denied(
                0,
                getNextLeakTime(currentTime),
                "REDIS_LEAKY_BUCKET",
                "Bucket is full"
            );
        }
    }
    
    /**
     * 상태 조회 (요청 추가 없이)
     */
    public RateLimitResult getStatus(String key, RateLimitConfig config) {
        String redisKey = "leaky_bucket:" + key;
        long currentTime = System.currentTimeMillis();
        
        // 상태만 조회하는 Lua 스크립트
        String statusScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local leak_rate = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local last_leak_key = key .. ':last_leak'
            local last_leak = tonumber(redis.call('GET', last_leak_key)) or current_time
            
            -- leak 시뮬레이션 (실제로는 제거하지 않음)
            local elapsed = math.max(0, current_time - last_leak)
            local requests_to_leak = math.floor(elapsed / 1000) * leak_rate
            
            local current_size = redis.call('ZCARD', key)
            local simulated_size = math.max(0, current_size - requests_to_leak)
            
            return capacity - simulated_size
            """;
        
        RedisScript<Long> statusRedisScript = RedisScript.of(statusScript, Long.class);
        
        Long remainingCapacity = redisTemplate.execute(
            statusRedisScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getRefillRate()),
            String.valueOf(currentTime)
        );
        
        return RateLimitResult.allowed(
            remainingCapacity != null ? remainingCapacity : config.getCapacity(),
            getNextLeakTime(currentTime),
            "REDIS_LEAKY_BUCKET",
            "Current bucket status"
        );
    }
    
    /**
     * 다음 누출 시간 계산
     */
    private long getNextLeakTime(long currentTime) {
        return currentTime + 1000; // 1초 후
    }
}
