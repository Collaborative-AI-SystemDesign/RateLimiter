package com.example.demo.ratelimiter.controller;

import com.example.demo.ratelimiter.config.RateLimiterFactory;
import com.example.demo.ratelimiter.controller.request.AdminStatsRequest;
import com.example.demo.ratelimiter.controller.response.AdminResetResponse;
import com.example.demo.ratelimiter.controller.response.AdminStatsResponse;
import com.example.demo.ratelimiter.controller.response.AlgorithmTestResponse;
import com.example.demo.ratelimiter.core.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.Authentication;
// import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

/**
 * Rate Limiter 알고리즘별 테스트용 컨트롤러
 * 5가지 알고리즘별로 별도의 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class RateLimitTestController {
    
    private final RateLimiterFactory rateLimiterFactory;
    
    //1. Token Bucket 알고리즘 테스트 엔드포인트
    @GetMapping("/token-bucket")
    public ResponseEntity<AlgorithmTestResponse> tokenBucketTest() {
        Long userId = getCurrentUserId();
        
        AlgorithmTestResponse response = AlgorithmTestResponse.of(
                "token-bucket",
                "Token Bucket Algorithm Test - 버스트 트래픽 허용, 평균 속도 제어",
                "토큰을 일정한 속도로 버킷에 추가하고, 요청 시 토큰을 소모",
                "/api/test/token-bucket",
                userId
        );
        
        log.info("Token Bucket test endpoint called for user: {}", userId);
        return ResponseEntity.ok(response);
    }
    
    //2. Leaky Bucket 알고리즘 테스트 엔드포인트
    @GetMapping("/leaky-bucket")
    public ResponseEntity<AlgorithmTestResponse> leakyBucketTest() {
        Long userId = getCurrentUserId();
        
        AlgorithmTestResponse response = AlgorithmTestResponse.of(
                "leaky-bucket",
                "Leaky Bucket Algorithm Test - 트래픽 평활화, 버스트 방지",
                "요청을 버킷에 넣고 일정한 속도로 누출시켜 부드러운 트래픽 처리",
                "/api/test/leaky-bucket",
                userId
        );
        
        log.info("Leaky Bucket test endpoint called for user: {}", userId);
        return ResponseEntity.ok(response);
    }
    
    //3. Fixed Window Counter 알고리즘 테스트 엔드포인트
    @GetMapping("/fixed-window")
    public ResponseEntity<AlgorithmTestResponse> fixedWindowTest() {
        Long userId = getCurrentUserId();
        
        AlgorithmTestResponse response = AlgorithmTestResponse.of(
                "fixed-window",
                "Fixed Window Counter Algorithm Test - 고정된 시간 창 내 요청 수 제한",
                "고정된 시간 창(예: 1분) 내 요청 수를 카운트하여 제한",
                "/api/test/fixed-window",
                userId
        );

        log.info("Fixed Window Counter test endpoint called for user: {}", userId);
        return ResponseEntity.ok(response);
    }
    

    // 4. Sliding Window Log 알고리즘 테스트 엔드포인트
    @GetMapping("/sliding-window-log")
    public ResponseEntity<AlgorithmTestResponse> slidingWindowLogTest() {
        Long userId = getCurrentUserId();

        AlgorithmTestResponse response = AlgorithmTestResponse.of(
                "sliding-window-log",
                "Sliding Window Log Algorithm Test - 시간 기반 로그 기록",
                "요청 시간을 기록하고, 현재 윈도우 내 요청 수를 계산하여 제한",
                "/api/test/sliding-window-log",
                userId
        );

        log.info("Sliding Window Log test endpoint called for user: {}", userId);
        return ResponseEntity.ok(response);
    }
    

    // 5. Sliding Window Counter 알고리즘 테스트 엔드포인트
    @GetMapping("/sliding-window-counter")
    public ResponseEntity<AlgorithmTestResponse> slidingWindowCounterTest() {
        Long userId = getCurrentUserId();
        
        AlgorithmTestResponse response = AlgorithmTestResponse.of(
                "sliding-window-counter",
                "Sliding Window Counter Algorithm Test - 시간 기반 카운터",
                "요청 수를 시간 단위로 카운트하여 제한, 윈도우 크기 내에서 요청 수를 계산",
                "/api/test/sliding-window-counter",
                userId
        );
        
        log.info("Sliding Window Counter test endpoint called for user: {}", userId);
        return ResponseEntity.ok(response);
    }
    
    // =========================== 관리용 엔드포인트 ===========================
    
    //Rate Limiter 상태 조회 (관리용) - RequestBody로 요청 받기
    @PostMapping("/admin/stats")
    public ResponseEntity<AdminStatsResponse> getRateLimitStats(@RequestBody AdminStatsRequest request) {
        Long userId = request.getUserId();
        String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "token-bucket";
        
        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(algorithm);
        Map<String, Object> stats = rateLimiter.getStats(userId);
        
        AdminStatsResponse response = AdminStatsResponse.builder()
                .userId(userId)
                .algorithm(algorithm)
                .stats(stats)
                .timestamp(LocalDateTime.now())
                .build();
        
        log.info("Rate limit stats requested for user: {}, algorithm: {}", userId, algorithm);
        return ResponseEntity.ok(response);
    }
    
    //Rate Limiter 리셋 (관리용) - 모든 알고리즘 버킷 초기화
    @PostMapping("/admin/reset")
    public ResponseEntity<AdminResetResponse> resetAllRateLimits() {
        Long userId = getCurrentUserId();
        
        // 모든 알고리즘 타입 정의
        List<String> algorithms = Arrays.asList(
            "token-bucket", 
            "leaky-bucket", 
            "fixed-window", 
            "sliding-window-log", 
            "sliding-window-counter"
        );
        
        Map<String, String> resetResults = new HashMap<>();
        
        // 각 알고리즘별로 버킷 리셋
        for (String algorithm : algorithms) {
            try {
                RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(algorithm);
                rateLimiter.reset(userId);
                resetResults.put(algorithm, "success");
                log.debug("Reset {} rate limiter for user: {}", algorithm, userId);
            } catch (Exception e) {
                resetResults.put(algorithm, "failed: " + e.getMessage());
                log.warn("Failed to reset {} rate limiter for user: {}, error: {}", algorithm, userId, e.getMessage());
            }
        }
        
        AdminResetResponse response = AdminResetResponse.builder()
                .message("All rate limiters reset attempted")
                .userId(userId)
                .resetResults(resetResults)
                .timestamp(LocalDateTime.now())
                .build();
        
        log.info("All rate limiters reset for user: {}, results: {}", userId, resetResults);
        return ResponseEntity.ok(response);
    }
    
    // =========================== 유틸리티 메서드 ===========================
    

    // TODO: JWT 인증 필터 구현 시 SecurityContext에서 실제 userId 추출
    private Long getCurrentUserId() {
        // TODO: 실제 JWT 구현 시 SecurityContext에서 userId 추출
        // Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // return (Long) authentication.getPrincipal();
        
        // 임시 테스트용 사용자 ID
        return 123L;
    }
}
