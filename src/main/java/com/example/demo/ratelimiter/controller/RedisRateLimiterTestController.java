package com.example.demo.ratelimiter.controller;

import com.example.demo.ratelimiter.algo.bucket.RedisTokenBucketLimiter;
import com.example.demo.ratelimiter.algo.bucket.RedisLeakyBucketLimiter;
import com.example.demo.ratelimiter.algo.fixed.RedisFixedWindowRateLimiter;
import com.example.demo.ratelimiter.algo.RedisSlidingWindowCounterLimiter;
import com.example.demo.ratelimiter.algo.RedisSlidingWindowLogRateLimiter;
import com.example.demo.ratelimiter.annotation.RedisRateLimit;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import com.example.demo.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 기반 Rate Limiter 테스트 컨트롤러
 * 
 * 분산 환경에서 동작하는 Redis 기반 Rate Limiting 알고리즘들을 테스트할 수 있습니다.
 * 모든 상태가 Redis에 저장되어 여러 인스턴스 간 일관된 제한을 제공합니다.
 */
@RestController
@RequestMapping("/api/redis-rate-limit")
@RequiredArgsConstructor
public class RedisRateLimiterTestController {

    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final RedisLeakyBucketLimiter redisLeakyBucketLimiter;
    private final RedisFixedWindowRateLimiter redisFixedWindowRateLimiter;
    private final RedisSlidingWindowCounterLimiter redisSlidingWindowCounterLimiter;
    private final RedisSlidingWindowLogRateLimiter redisSlidingWindowLogRateLimiter;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Redis Token Bucket 테스트
     * 10개 토큰, 초당 2개 보충
     */
    @GetMapping("/test/redis-token-bucket")
    public ApiResponse<Map<String, Object>> testRedisTokenBucket() {
        String clientIp = getClientIp();
        RateLimitConfig config = RateLimitConfig.forTokenBucket(10, 5);
        RateLimitResult result = redisTokenBucketLimiter.tryAcquire(clientIp, config);
        
        return createResponse("Redis Token Bucket", result, 
            "🪣 Redis Token Bucket - 10개 토큰, 초당 2개 보충\n분산 환경에서 정확한 버스트 트래픽 제어");
    }

    /**
     * Redis Leaky Bucket 테스트  
     * 10개 용량, 초당 1개 처리
     */
    @GetMapping("/test/redis-leaky-bucket")
    public ApiResponse<Map<String, Object>> testRedisLeakyBucket() {
        String clientIp = getClientIp();
        RateLimitConfig config = RateLimitConfig.forTokenBucket(10, 5);
        RateLimitResult result = redisLeakyBucketLimiter.tryAcquire(clientIp, config);
        
        return createResponse("Redis Leaky Bucket", result,
            "💧 Redis Leaky Bucket - 10개 용량, 초당 1개 처리\n분산 환경에서 일정한 트래픽 평활화");
    }

    /**
     * Redis Fixed Window 테스트
     * 1분당 15개 요청
     */
    @GetMapping("/test/redis-fixed-window")
    public ApiResponse<Map<String, Object>> testRedisFixedWindow() {
        String clientIp = getClientIp();
        RateLimitConfig config = RateLimitConfig.forWindow(15, 15000);
        RateLimitResult result = redisFixedWindowRateLimiter.tryAcquire(clientIp, config);
        
        return createResponse("Redis Fixed Window", result,
            "🪟 Redis Fixed Window - 1분당 15개 요청\n분산 환경에서 간단하고 효율적인 제한");
    }

    /**
     * Redis Sliding Window Counter 테스트
     * 1분당 12개 요청
     */
    @GetMapping("/test/redis-sliding-counter")
    public ApiResponse<Map<String, Object>> testRedisSlidingCounter() {
        String clientIp = getClientIp();
        RateLimitConfig config = RateLimitConfig.forWindow(12, 15000);
        RateLimitResult result = redisSlidingWindowCounterLimiter.tryAcquire(clientIp, config);
        
        return createResponse("Redis Sliding Window Counter", result,
            "📊 Redis Sliding Counter - 1분당 12개 요청\n분산 환경에서 Fixed Window 문제 완화");
    }

    /**
     * Redis Sliding Window Log 테스트
     * 1분당 8개 요청
     */
    @GetMapping("/test/redis-sliding-log")
    public ApiResponse<Map<String, Object>> testRedisSlidingLog() {
        String clientIp = getClientIp();
        RateLimitConfig config = RateLimitConfig.forWindow(8, 15000);
        RateLimitResult result = redisSlidingWindowLogRateLimiter.tryAcquire(clientIp, config);
        
        return createResponse("Redis Sliding Window Log", result,
            "📝 Redis Sliding Log - 1분당 8개 요청\n분산 환경에서 가장 정확한 제한 (메모리 사용량 높음)");
    }

    /**
     * 모든 Redis 알고리즘 상태 조회
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getAllStatus() {
        String clientIp = getClientIp();
        Map<String, Object> status = new HashMap<>();
        
        // 각 알고리즘별 상태 조회
        status.put("redis_token_bucket", redisTokenBucketLimiter.getStatus(clientIp));
        status.put("redis_leaky_bucket", redisLeakyBucketLimiter.getStatus(clientIp));
        status.put("redis_fixed_window", redisFixedWindowRateLimiter.getStatus(clientIp));
        status.put("redis_sliding_counter", redisSlidingWindowCounterLimiter.getStatus(clientIp));
        status.put("redis_sliding_log", redisSlidingWindowLogRateLimiter.getStatus(clientIp));
        
        status.put("client_ip", clientIp);
        status.put("timestamp", System.currentTimeMillis());
        
        return ApiResponse.success("🔍 모든 Redis Rate Limiter 상태 조회 완료", status);
    }

    /**
     * Redis 알고리즘 비교 정보
     */
    @GetMapping("/algorithms")
    public ApiResponse<Map<String, Object>> getRedisAlgorithmsInfo() {
        Map<String, Object> algorithms = new HashMap<>();
        
        Map<String, Object> tokenBucket = new HashMap<>();
        tokenBucket.put("name", "Redis Token Bucket");
        tokenBucket.put("description", "분산 환경에서 버스트 트래픽을 허용하면서도 평균 속도를 제한");
        tokenBucket.put("pros", "버스트 허용, 메모리 효율적, 분산 일관성");
        tokenBucket.put("cons", "네트워크 지연, Redis 의존성");
        tokenBucket.put("use_case", "API 호출 제한, 사용자별 요청 제한");
        tokenBucket.put("data_structure", "Redis Hash (토큰 수, 마지막 보충 시간)");
        
        Map<String, Object> leakyBucket = new HashMap<>();
        leakyBucket.put("name", "Redis Leaky Bucket");
        leakyBucket.put("description", "분산 환경에서 일정한 속도로 요청을 처리하여 트래픽 평활화");
        leakyBucket.put("pros", "트래픽 평활화, 백엔드 보호, 분산 일관성");
        leakyBucket.put("cons", "버스트 불가, 네트워크 지연, Redis 의존성");
        leakyBucket.put("use_case", "백엔드 시스템 보호, 대역폭 제어");
        leakyBucket.put("data_structure", "Redis Sorted Set (요청 타임스탬프)");
        
        Map<String, Object> fixedWindow = new HashMap<>();
        fixedWindow.put("name", "Redis Fixed Window");
        fixedWindow.put("description", "분산 환경에서 고정된 시간 윈도우 내 요청 수를 제한");
        fixedWindow.put("pros", "구현 간단, 메모리 효율적, 분산 일관성");
        fixedWindow.put("cons", "윈도우 경계 버스트, 네트워크 지연");
        fixedWindow.put("use_case", "간단한 API 제한, 시간당 요청 제한");
        fixedWindow.put("data_structure", "Redis String (카운터 + TTL)");
        
        Map<String, Object> slidingCounter = new HashMap<>();
        slidingCounter.put("name", "Redis Sliding Window Counter");
        slidingCounter.put("description", "분산 환경에서 가중 평균으로 슬라이딩 윈도우 효과를 근사");
        slidingCounter.put("pros", "Fixed Window 문제 완화, 메모리 효율적, 분산 일관성");
        slidingCounter.put("cons", "근사치 계산, 복잡한 로직, 네트워크 지연");
        slidingCounter.put("use_case", "정확도와 성능의 균형이 필요한 경우");
        slidingCounter.put("data_structure", "Redis String (현재/이전 윈도우 카운터)");
        
        Map<String, Object> slidingLog = new HashMap<>();
        slidingLog.put("name", "Redis Sliding Window Log");
        slidingLog.put("description", "분산 환경에서 모든 요청을 로그로 저장하여 정확한 제한");
        slidingLog.put("pros", "가장 정확한 제한, 균등한 분산, 분산 일관성");
        slidingLog.put("cons", "메모리 사용량 높음, 성능 오버헤드, 확장성 제한");
        slidingLog.put("use_case", "정확성이 중요한 결제 시스템, 중요한 API");
        slidingLog.put("data_structure", "Redis Sorted Set (모든 요청 타임스탬프)");
        
        algorithms.put("redis_token_bucket", tokenBucket);
        algorithms.put("redis_leaky_bucket", leakyBucket);
        algorithms.put("redis_fixed_window", fixedWindow);
        algorithms.put("redis_sliding_counter", slidingCounter);
        algorithms.put("redis_sliding_log", slidingLog);
        
        return ApiResponse.success("🔧 Redis Rate Limiting 알고리즘 정보", algorithms);
    }

    /**
     * 어노테이션 기반 테스트 - Redis Token Bucket
     */
    @GetMapping("/annotation/redis-token-bucket")
    @RedisRateLimit(
        algorithm = RedisRateLimit.RedisAlgorithmType.REDIS_TOKEN_BUCKET,
        limit = 5,
        refillRate = 1,
        message = "Redis Token Bucket 어노테이션으로 제한됨!"
    )
    public ApiResponse<String> testAnnotationRedisTokenBucket() {
        return ApiResponse.success(
            "✅ Redis Token Bucket 어노테이션 테스트 성공!",
            "🎯 @RedisRateLimit 어노테이션이 정상 동작합니다"
        );
    }

    /**
     * 어노테이션 기반 테스트 - Redis Fixed Window
     */
    @GetMapping("/annotation/redis-fixed-window")
    @RedisRateLimit(
        algorithm = RedisRateLimit.RedisAlgorithmType.REDIS_FIXED_WINDOW,
        limit = 3,
        windowSeconds = 30,
        message = "Redis Fixed Window 어노테이션으로 제한됨!"
    )
    public ApiResponse<String> testAnnotationRedisFixedWindow() {
        return ApiResponse.success(
            "✅ Redis Fixed Window 어노테이션 테스트 성공!",
            "🎯 30초당 3개 요청으로 제한됩니다"
        );
    }

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        }
        return "unknown";
    }

    /**
     * 디버깅용 엔드포인트 - Sliding Window Log 상태 확인
     */
    @GetMapping("/debug/sliding-log/{ip}")
    public ApiResponse<Map<String, Object>> debugSlidingLog(@PathVariable String ip) {
        Map<String, Object> debugInfo = new HashMap<>();
        String key = "sliding_window_log:" + ip;
        String debugKey = key + ":debug";
        
        // Redis에서 디버깅 정보 가져오기
        String debugData = redisTemplate.opsForValue().get(debugKey);
        debugInfo.put("debug_data", debugData);
        debugInfo.put("current_time", System.currentTimeMillis());
        
        return ApiResponse.success("디버깅 정보", debugInfo);
    }

    /**
     * 응답 생성 헬퍼 메서드
     */
    private ApiResponse<Map<String, Object>> createResponse(String algorithm, RateLimitResult result, String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", algorithm);
        data.put("allowed", result.isAllowed());
        data.put("remaining_tokens", result.getRemainingTokens());
        data.put("reset_time", result.getResetTime());
        data.put("reset_time_readable", new java.util.Date(result.getResetTime()));
        data.put("algorithm_type", result.getAlgorithm());
        data.put("message", result.getMessage());
        data.put("description", description);
        data.put("client_ip", getClientIp());
        data.put("timestamp", System.currentTimeMillis());
        
        if (result.isAllowed()) {
            return ApiResponse.success("✅ 요청이 허용되었습니다", data);
        } else {
            return ApiResponse.fail("❌ " + result.getMessage());
        }
    }
}
