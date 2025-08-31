package com.example.demo.ratelimiter.algo;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import com.example.demo.ratelimiter.common.RedisRateLimiter;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Redis Sliding Window Counter Algorithm
 * 
 * 동작 원리:
 * - 현재 윈도우와 이전 윈도우의 카운터를 조합하여 계산
 * - 가중 평균을 사용하여 슬라이딩 윈도우 효과를 근사
 * - Redis Hash를 사용하여 윈도우별 카운터 관리
 * - Lua 스크립트로 원자적 연산 보장
 * 
 * 장점:
 * - 메모리 효율적 (키당 2개의 윈도우 카운터만 저장)
 * - Fixed Window의 버스트 문제 완화
 * - 분산 환경에서 일관된 제한
 * - 성능이 우수함
 * 
 * 단점:
 * - 근사치 계산 (완전히 정확하지 않음)
 * - 윈도우 경계에서 여전히 약간의 부정확성 존재
 */
@Component
public class RedisSlidingWindowCounterLimiter implements RedisRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig defaultConfig;
    private final RedisScript<Long> slidingWindowScript;

    // Lua 스크립트 - 슬라이딩 윈도우 카운터 로직
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window_size = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        
        -- 현재 윈도우와 이전 윈도우 계산
        local current_window = math.floor(current_time / window_size)
        local previous_window = current_window - 1
        
        -- 윈도우별 키 생성
        local current_key = key .. ':' .. current_window
        local previous_key = key .. ':' .. previous_window
        
        -- 현재 윈도우에서의 진행률 계산 (0.0 ~ 1.0)
        local window_start_time = current_window * window_size
        local time_into_window = current_time - window_start_time
        local percentage_of_current_window = time_into_window / window_size
        
        -- 현재 윈도우와 이전 윈도우의 카운트 가져오기
        local current_count = tonumber(redis.call('GET', current_key)) or 0
        local previous_count = tonumber(redis.call('GET', previous_key)) or 0
        
        -- 가중 평균으로 추정 요청 수 계산
        local estimated_previous_count = previous_count * (1.0 - percentage_of_current_window)
        local estimated_count = math.floor(estimated_previous_count + current_count)
        
        -- 제한 확인
        if estimated_count < limit then
            -- 현재 윈도우 카운터 증가
            local new_count = redis.call('INCR', current_key)
            -- TTL 설정 (윈도우 크기의 2배로 설정하여 이전 윈도우 데이터 유지)
            redis.call('EXPIRE', current_key, math.ceil(window_size / 1000) * 2)
            
            return 1  -- 허용됨
        else
            return 0  -- 거부됨
        end
        """;

    public RedisSlidingWindowCounterLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.defaultConfig = RateLimitConfig.forWindow(10, 60000); // 1분당 10개 요청
        this.slidingWindowScript = RedisScript.of(LUA_SCRIPT, Long.class);
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
        String redisKey = "sliding_window_counter:" + key;
        long currentTime = System.currentTimeMillis();
        long currentWindow = currentTime / defaultConfig.getWindowSizeMs();
        long previousWindow = currentWindow - 1;
        
        redisTemplate.delete(redisKey + ":" + currentWindow);
        redisTemplate.delete(redisKey + ":" + previousWindow);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        String redisKey = "sliding_window_counter:" + key;
        long currentTime = System.currentTimeMillis();
        
        Long result = redisTemplate.execute(
            slidingWindowScript,
            Collections.singletonList(redisKey),
            String.valueOf(config.getCapacity()),
            String.valueOf(config.getWindowSizeMs()),
            String.valueOf(currentTime)
        );
        
        if (result != null && result == 1) {
            // 다시 상태를 조회하여 정확한 남은 용량 계산
            RateLimitResult status = getStatus(key, config);
            return RateLimitResult.allowed(
                status.getRemainingTokens(),
                getNextWindowStart(currentTime, config.getWindowSizeMs()),
                "REDIS_SLIDING_WINDOW_COUNTER",
                "Request counted in sliding window"
            );
        } else {
            return RateLimitResult.denied(
                0,
                getNextWindowStart(currentTime, config.getWindowSizeMs()),
                "REDIS_SLIDING_WINDOW_COUNTER",
                "Sliding window counter limit exceeded"
            );
        }
    }
    
    /**
     * 상태 조회 (카운터 증가 없이)
     */
    public RateLimitResult getStatus(String key, RateLimitConfig config) {
        String redisKey = "sliding_window_counter:" + key;
        long currentTime = System.currentTimeMillis();
        
        // 상태만 조회하는 Lua 스크립트
        String statusScript = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            
            local current_window = math.floor(current_time / window_size)
            local previous_window = current_window - 1
            
            local current_key = key .. ':' .. current_window
            local previous_key = key .. ':' .. previous_window
            
            local window_start_time = current_window * window_size
            local time_into_window = current_time - window_start_time
            local percentage_of_current_window = time_into_window / window_size
            
            local current_count = tonumber(redis.call('GET', current_key)) or 0
            local previous_count = tonumber(redis.call('GET', previous_key)) or 0
            
            local estimated_previous_count = previous_count * (1.0 - percentage_of_current_window)
            local estimated_count = math.floor(estimated_previous_count + current_count)
            
            return math.max(0, limit - estimated_count)
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
            getNextWindowStart(currentTime, config.getWindowSizeMs()),
            "REDIS_SLIDING_WINDOW_COUNTER",
            "Current sliding window counter status"
        );
    }
    
    /**
     * 다음 윈도우 시작 시간 계산
     */
    private long getNextWindowStart(long currentTime, long windowSizeMs) {
        long currentWindow = currentTime / windowSizeMs;
        return (currentWindow + 1) * windowSizeMs;
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
