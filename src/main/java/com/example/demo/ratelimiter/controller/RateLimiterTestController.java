package com.example.demo.ratelimiter.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.ratelimiter.annotation.RateLimit;
import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiter 테스트 컨트롤러
 * 각 알고리즘별로 테스트할 수 있는 엔드포인트를 제공합니다.
 */
@RestController
@RequestMapping("/api/rate-limit")
public class RateLimiterTestController {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // =========================== 어노테이션 기반 테스트 ===========================
    
    /**
     * Token Bucket 알고리즘 테스트
     * 5개 토큰, 초당 2개 보충
     */
    @GetMapping("/test/token-bucket")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.TOKEN_BUCKET,
        limit = 5,
        refillRate = 2,
        message = "Token Bucket 제한 초과! 잠시 후 다시 시도해주세요."
    )
    public ApiResponse<String> testTokenBucket() {
        return ApiResponse.success("Token Bucket 알고리즘 테스트 성공! 🪣✨");
    }
    
    /**
     * Leaky Bucket 알고리즘 테스트
     * 5개 용량, 초당 1개 처리
     */
    @GetMapping("/test/leaky-bucket")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.LEAKY_BUCKET,
        limit = 5,
        refillRate = 1,
        message = "Leaky Bucket 제한 초과! 버킷이 가득 찼습니다."
    )
    public ApiResponse<String> testLeakyBucket() {
        return ApiResponse.success("Leaky Bucket 알고리즘 테스트 성공! 💧🪣");
    }
    
    /**
     * Fixed Window 알고리즘 테스트
     * 1분당 10개 요청
     */
    @GetMapping("/test/fixed-window")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.FIXED_WINDOW,
        limit = 10,
        windowSeconds = 60,
        message = "Fixed Window 제한 초과! 1분 후 다시 시도해주세요."
    )
    public ApiResponse<String> testFixedWindow() {
        return ApiResponse.success("Fixed Window 알고리즘 테스트 성공! 🪟📊");
    }
    
    /**
     * Sliding Window Log 알고리즘 테스트
     * 30초당 8개 요청
     */
    @GetMapping("/test/sliding-log")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.SLIDING_WINDOW_LOG,
        limit = 8,
        windowSeconds = 30,
        message = "Sliding Window Log 제한 초과! 정확한 시간 기반 제한입니다."
    )
    public ApiResponse<String> testSlidingWindowLog() {
        return ApiResponse.success("Sliding Window Log 알고리즘 테스트 성공! 📋🔄");
    }
    
    /**
     * Sliding Window Counter 알고리즘 테스트
     * 30초당 8개 요청
     */
    @GetMapping("/test/sliding-counter")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.SLIDING_WINDOW_COUNTER,
        limit = 8,
        windowSeconds = 30,
        message = "Sliding Window Counter 제한 초과! 근사치 기반 제한입니다."
    )
    public ApiResponse<String> testSlidingWindowCounter() {
        return ApiResponse.success("Sliding Window Counter 알고리즘 테스트 성공! 📊🔄");
    }
    
    /**
     * 사용자 기반 Rate Limiting 테스트
     */
    @GetMapping("/test/user-based")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.TOKEN_BUCKET,
        keyType = RateLimit.KeyType.USER,
        limit = 3,
        refillRate = 1,
        message = "사용자별 제한 초과! Authorization 헤더를 확인해주세요."
    )
    public ApiResponse<String> testUserBased() {
        return ApiResponse.success("사용자 기반 Rate Limiting 테스트 성공! 👤🔒");
    }
    
    /**
     * 커스텀 키 기반 Rate Limiting 테스트
     */
    @GetMapping("/test/custom-key/{category}")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.FIXED_WINDOW,
        keyType = RateLimit.KeyType.CUSTOM,
        customKey = "category",
        limit = 5,
        windowSeconds = 60,
        message = "카테고리별 제한 초과!"
    )
    public ApiResponse<String> testCustomKey(@PathVariable String category) {
        return ApiResponse.success(String.format("카테고리 '%s' 커스텀 키 테스트 성공! 🏷️🔑", category));
    }

    /**
     * HTTP 메서드 + 엔드포인트 기반 Rate Limiting 테스트
     */
    @GetMapping("/test/api-based")
    @RateLimit(
            algorithm = RateLimit.AlgorithmType.TOKEN_BUCKET,
            keyType = RateLimit.KeyType.API,
            limit = 3,
            refillRate = 1,
            message = "엔드포인트별 제한 초과!"
    )
    public ApiResponse<String> testApiBased() {
        return ApiResponse.success("HTTP 메서드 + 엔드포인트 기반 Rate Limiting 테스트 성공! 👤🔒");
    }
    
    // =========================== 수동 테스트 API ===========================
    
    /**
     * 수동으로 특정 알고리즘 테스트
     */
    @PostMapping("/manual/{algorithm}")
    public ApiResponse<RateLimitResult> manualTest(
            @PathVariable String algorithm, 
            @RequestParam(defaultValue = "test-key") String key) {
        
        RateLimiter limiter = getRateLimiter(algorithm);
        if (limiter == null) {
            return ApiResponse.fail("지원하지 않는 알고리즘입니다: " + algorithm);
        }
        
        RateLimitResult result = limiter.tryAcquire(key);
        
        if (result.isAllowed()) {
            return ApiResponse.success("요청 허용됨", result);
        } else {
            return ApiResponse.success("요청 거부됨", result);
        }
    }
    
    /**
     * 특정 알고리즘의 상태 조회
     */
    @GetMapping("/status/{algorithm}")
    public ApiResponse<RateLimitResult> getStatus(
            @PathVariable String algorithm,
            @RequestParam(defaultValue = "test-key") String key) {
        
        RateLimiter limiter = getRateLimiter(algorithm);
        if (limiter == null) {
            return ApiResponse.fail("지원하지 않는 알고리즘입니다: " + algorithm);
        }
        
        RateLimitResult result = limiter.getStatus(key);
        return ApiResponse.success("상태 조회 완료", result);
    }
    
    /**
     * 특정 키의 Rate Limit 상태 초기화
     */
    @DeleteMapping("/reset/{algorithm}")
    public ApiResponse<String> resetLimit(
            @PathVariable String algorithm,
            @RequestParam(defaultValue = "test-key") String key) {
        
        RateLimiter limiter = getRateLimiter(algorithm);
        if (limiter == null) {
            return ApiResponse.fail("지원하지 않는 알고리즘입니다: " + algorithm);
        }
        
        limiter.reset(key);
        return ApiResponse.success(String.format("'%s' 키의 %s 제한이 초기화되었습니다.", key, algorithm));
    }
    
    /**
     * 모든 알고리즘 정보 조회
     */
    @GetMapping("/algorithms")
    public ApiResponse<Map<String, Object>> getAllAlgorithms() {
        Map<String, Object> algorithms = new HashMap<>();
        
        for (RateLimit.AlgorithmType type : RateLimit.AlgorithmType.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", type.name());
            info.put("beanName", type.getBeanName());
            info.put("description", getAlgorithmDescription(type));
            algorithms.put(type.name(), info);
        }
        
        return ApiResponse.success("지원하는 모든 알고리즘", algorithms);
    }
    
    /**
     * Rate Limiter 빈 조회
     */
    private RateLimiter getRateLimiter(String algorithm) {
        try {
            String beanName = algorithm.toLowerCase() + "Limiter";
            return applicationContext.getBean(beanName, RateLimiter.class);
        } catch (Exception e) {
            // 알고리즘 이름으로 직접 조회 시도
            for (RateLimit.AlgorithmType type : RateLimit.AlgorithmType.values()) {
                if (type.name().equalsIgnoreCase(algorithm)) {
                    try {
                        return applicationContext.getBean(type.getBeanName(), RateLimiter.class);
                    } catch (Exception ex) {
                        break;
                    }
                }
            }
            return null;
        }
    }
    
    /**
     * 알고리즘 설명 반환
     */
    private String getAlgorithmDescription(RateLimit.AlgorithmType type) {
        switch (type) {
            case TOKEN_BUCKET:
                return "토큰 버킷 - 버스트 트래픽 허용, 일정한 속도로 토큰 보충";
            case LEAKY_BUCKET:
                return "리키 버킷 - 일정한 출력 속도 보장, 트래픽 평활화";
            case FIXED_WINDOW:
                return "고정 윈도우 - 간단한 구현, 윈도우별 카운팅";
            case SLIDING_WINDOW_LOG:
                return "슬라이딩 윈도우 로그 - 정확한 제한, 모든 요청 타임스탬프 저장";
            case SLIDING_WINDOW_COUNTER:
                return "슬라이딩 윈도우 카운터 - 메모리 효율적, 근사치 계산";
            default:
                return "알 수 없는 알고리즘";
        }
    }
} 