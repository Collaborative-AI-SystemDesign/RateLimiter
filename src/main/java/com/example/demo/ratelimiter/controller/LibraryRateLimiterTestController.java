package com.example.demo.ratelimiter.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.ratelimiter.annotation.LibraryRateLimit;
import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 라이브러리 기반 Rate Limiter 테스트 컨트롤러
 * 
 * 🎯 특징:
 * - Bucket4j, Resilience4j, Guava 라이브러리 테스트
 * - 수동 구현과 성능/기능 비교 가능
 * - 라이브러리별 특화 기능 시연
 * 
 * 📚 수동 구현 컨트롤러와의 차이점:
 * - 라이브러리별 고급 기능 활용
 * - 더 간단한 설정
 * - 검증된 구현체 사용
 */
@RestController
@RequestMapping("/api/library-rate-limit")
public class LibraryRateLimiterTestController {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // =========================== Bucket4j 테스트 ===========================
    
    /**
     * Bucket4j 기본 테스트
     */
    @GetMapping("/test/bucket4j")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.BUCKET4J,
        limit = 5,
        periodSeconds = 60,
        message = "Bucket4j 제한 초과! 검증된 Token Bucket 구현체입니다."
    )
    public ApiResponse<String> testBucket4j() {
        return ApiResponse.success("🪣 Bucket4j 테스트 성공! (검증된 라이브러리)");
    }
    
    /**
     * Bucket4j 다중 제한 테스트 (시간당 + 분당)
     */
    @GetMapping("/test/bucket4j-multi")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.BUCKET4J,
        limit = 3,
        periodSeconds = 30,
        message = "Bucket4j 다중 제한! 시간당 100개 + 분당 10개 제한"
    )
    public ApiResponse<String> testBucket4jMultiLimit() {
        return ApiResponse.success("🪣⚡ Bucket4j 다중 제한 테스트 성공! (고급 기능)");
    }
    
    // =========================== Resilience4j 테스트 ===========================
    
    /**
     * Resilience4j 기본 테스트
     */
    @GetMapping("/test/resilience4j")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.RESILIENCE4J,
        limit = 8,
        periodSeconds = 60,
        message = "Resilience4j 제한 초과! Spring Boot 완벽 통합 라이브러리입니다."
    )
    public ApiResponse<String> testResilience4j() {
        return ApiResponse.success("🛡️ Resilience4j 테스트 성공! (Spring Boot 통합)");
    }
    
    /**
     * Resilience4j 빠른 제한 테스트 (초당)
     */
    @GetMapping("/test/resilience4j-fast")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.RESILIENCE4J,
        limit = 3,
        periodSeconds = 1,
        message = "Resilience4j 초당 제한! 빠른 응답"
    )
    public ApiResponse<String> testResilience4jFast() {
        return ApiResponse.success("🛡️⚡ Resilience4j 빠른 제한 테스트 성공!");
    }
    
    // =========================== Guava 테스트 ===========================
    
    /**
     * Guava 기본 테스트
     */
    @GetMapping("/test/guava")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.GUAVA,
        limit = 2.0,  // 초당 2개
        timeoutMs = 100,
        message = "Guava 제한 초과! Google의 간단한 구현체입니다."
    )
    public ApiResponse<String> testGuava() {
        return ApiResponse.success("🔍 Guava 테스트 성공! (Google 구현체)");
    }
    
    /**
     * Guava 부드러운 버스트 테스트
     */
    @GetMapping("/test/guava-smooth")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.GUAVA,
        limit = 5.0,  // 초당 5개
        timeoutMs = 50,
        message = "Guava Smooth 제한! 부드러운 버스트 처리"
    )
    public ApiResponse<String> testGuavaSmooth() {
        return ApiResponse.success("🔍💫 Guava Smooth 테스트 성공! (부드러운 처리)");
    }
    
    // =========================== 키 타입별 테스트 ===========================
    
    /**
     * 사용자 기반 테스트 (라이브러리)
     */
    @GetMapping("/test/user-based")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.BUCKET4J,
        keyType = LibraryRateLimit.KeyType.USER,
        limit = 3,
        periodSeconds = 60,
        message = "라이브러리 기반 사용자별 제한!"
    )
    public ApiResponse<String> testUserBased() {
        return ApiResponse.success("👤🔒 라이브러리 기반 사용자별 테스트 성공!");
    }
    
    /**
     * 커스텀 키 테스트 (라이브러리)
     */
    @GetMapping("/test/custom/{category}")
    @LibraryRateLimit(
        library = LibraryRateLimit.LibraryType.RESILIENCE4J,
        keyType = LibraryRateLimit.KeyType.CUSTOM,
        customKey = "category",
        limit = 4,
        periodSeconds = 30,
        message = "라이브러리 기반 카테고리별 제한!"
    )
    public ApiResponse<String> testCustomKey(@PathVariable String category) {
        return ApiResponse.success(String.format("🏷️🔑 카테고리 '%s' 라이브러리 기반 테스트 성공!", category));
    }
    
    // =========================== 수동 테스트 & 비교 API ===========================
    
    /**
     * 라이브러리별 수동 테스트
     */
    @PostMapping("/manual/{library}")
    public ApiResponse<RateLimitResult> manualTest(
            @PathVariable String library,
            @RequestParam(defaultValue = "test-key") String key) {
        
        RateLimiter rateLimiter = getLibraryRateLimiter(library);
        if (rateLimiter == null) {
            return ApiResponse.fail("지원하지 않는 라이브러리입니다: " + library);
        }
        
        RateLimitResult result = rateLimiter.tryAcquire(key);
        
        String status = result.isAllowed() ? "✅ 허용됨" : "❌ 거부됨";
        return ApiResponse.success(status + " (라이브러리: " + library + ")", result);
    }
    
    /**
     * 라이브러리별 상태 조회
     */
    @GetMapping("/status/{library}")
    public ApiResponse<RateLimitResult> getLibraryStatus(
            @PathVariable String library,
            @RequestParam(defaultValue = "test-key") String key) {
        
        RateLimiter rateLimiter = getLibraryRateLimiter(library);
        if (rateLimiter == null) {
            return ApiResponse.fail("지원하지 않는 라이브러리입니다: " + library);
        }
        
        RateLimitResult result = rateLimiter.getStatus(key);
        return ApiResponse.success("라이브러리 상태 조회 완료", result);
    }
    
    /**
     * 라이브러리별 리셋
     */
    @DeleteMapping("/reset/{library}")
    public ApiResponse<String> resetLibraryLimit(
            @PathVariable String library,
            @RequestParam(defaultValue = "test-key") String key) {
        
        RateLimiter rateLimiter = getLibraryRateLimiter(library);
        if (rateLimiter == null) {
            return ApiResponse.fail("지원하지 않는 라이브러리입니다: " + library);
        }
        
        rateLimiter.reset(key);
        return ApiResponse.success(String.format("'%s' 키의 %s 라이브러리 제한이 초기화되었습니다.", key, library));
    }
    
    /**
     * 지원하는 모든 라이브러리 정보
     */
    @GetMapping("/libraries")
    public ApiResponse<Map<String, Object>> getAllLibraries() {
        Map<String, Object> libraries = new HashMap<>();
        
        for (LibraryRateLimit.LibraryType type : LibraryRateLimit.LibraryType.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", type.name());
            info.put("beanName", type.getBeanName());
            info.put("description", type.getDescription());
            info.put("advantages", getLibraryAdvantages(type));
            libraries.put(type.name(), info);
        }
        
        return ApiResponse.success("지원하는 모든 라이브러리", libraries);
    }
    
    /**
     * 수동 구현 vs 라이브러리 비교
     */
    @GetMapping("/comparison")
    public ApiResponse<Map<String, Object>> getComparison() {
        Map<String, Object> comparison = new HashMap<>();
        
        // 수동 구현 정보
        Map<String, Object> manual = new HashMap<>();
        manual.put("description", "직접 구현한 5가지 알고리즘");
        manual.put("algorithms", new String[]{"Token Bucket", "Leaky Bucket", "Fixed Window", "Sliding Window Log", "Sliding Window Counter"});
        manual.put("codeLines", "~500줄");
        manual.put("complexity", "높음");
        manual.put("learningValue", "⭐⭐⭐⭐⭐");
        manual.put("productionReady", "⚠️ 추가 테스트 필요");
        
        // 라이브러리 구현 정보
        Map<String, Object> library = new HashMap<>();
        library.put("description", "검증된 라이브러리 사용");
        library.put("libraries", new String[]{"Bucket4j", "Resilience4j", "Guava"});
        library.put("codeLines", "~100줄");
        library.put("complexity", "낮음");
        library.put("learningValue", "⭐⭐");
        library.put("productionReady", "✅ 즉시 사용 가능");
        
        comparison.put("manual", manual);
        comparison.put("library", library);
        comparison.put("recommendation", "학습용: 수동 구현, 프로덕션: 라이브러리 사용");
        
        return ApiResponse.success("수동 구현 vs 라이브러리 비교", comparison);
    }
    
    /**
     * 라이브러리 Rate Limiter 빈 조회
     */
    private RateLimiter getLibraryRateLimiter(String library) {
        try {
            String beanName = library.toLowerCase() + "RateLimiter";
            return applicationContext.getBean(beanName, RateLimiter.class);
        } catch (Exception e) {
            for (LibraryRateLimit.LibraryType type : LibraryRateLimit.LibraryType.values()) {
                if (type.name().equalsIgnoreCase(library)) {
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
     * 라이브러리별 장점 반환
     */
    private String[] getLibraryAdvantages(LibraryRateLimit.LibraryType type) {
        switch (type) {
            case BUCKET4J:
                return new String[]{
                    "검증된 Token Bucket 구현",
                    "다중 제한 지원 (시간당 + 분당)",
                    "Redis 분산 지원",
                    "메트릭 자동 수집"
                };
            case RESILIENCE4J:
                return new String[]{
                    "Spring Boot 완벽 통합",
                    "Circuit Breaker와 연동",
                    "Actuator 메트릭 자동 노출",
                    "설정 파일 기반 관리"
                };
            case GUAVA:
                return new String[]{
                    "Google 검증 라이브러리",
                    "매우 간단한 API",
                    "부드러운 버스트 처리",
                    "최소한의 설정"
                };
            default:
                return new String[]{"알 수 없는 라이브러리"};
        }
    }
} 