package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Leaky Bucket Rate Limiter 테스트
 * 성공/실패 케이스를 모두 검증합니다.
 */
@SpringBootTest
@Slf4j
@DisplayName("Leaky Bucket Rate Limiter 테스트")
class LeakyBucketRateLimiterTest {

    private LeakyBucketRateLimiter rateLimiter;
    private MockHttpServletRequest request;
    private final Long testUserId = 123L;

    @BeforeEach
    void setUp() {
        // 용량 1, 누출율 0.5/초로 설정하여 빠른 테스트 가능
        rateLimiter = new LeakyBucketRateLimiter(1, 0.5); // 1개 용량, 0.5 누출율
        request = new MockHttpServletRequest();
        log.info("Leaky Bucket Rate Limiter 초기화 완료 - capacity: 1, leakRate: 0.5");
    }

    @Test
    @DisplayName("성공 케이스 - 첫 번째 요청은 허용되어야 함")
    void testSuccessCase() {
        log.info("=== Leaky Bucket 성공 케이스 테스트 시작 ===");
        
        // 첫 번째 요청 - 성공해야 함
        RateLimitResult result = rateLimiter.allowRequest(testUserId, request);
        
        assertTrue(result.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        assertEquals("leaky-bucket", result.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(result.getResetTimeMillis() > 0, "리셋 시간이 설정되어야 합니다");
        
        log.info("Leaky Bucket 성공 테스트 완료 - remaining: {}, algorithm: {}", 
                result.getRemainingRequests(), result.getAlgorithm());
    }

    @Test
    @DisplayName("실패 케이스 - 용량 초과 시 요청이 거부되어야 함")
    void testFailureCase() {
        log.info("=== Leaky Bucket 실패 케이스 테스트 시작 ===");
        
        // 더 엄격한 테스트를 위해 용량을 2, 누출율을 낮게 설정
        LeakyBucketRateLimiter strictRateLimiter = new LeakyBucketRateLimiter(2, 0.1);
        
        // 첫 번째와 두 번째 요청으로 버킷을 가득 채움
        RateLimitResult firstResult = strictRateLimiter.allowRequest(testUserId, request);
        assertTrue(firstResult.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        log.info("첫 번째 요청 성공 - remaining: {}", firstResult.getRemainingRequests());
        
        RateLimitResult secondResult = strictRateLimiter.allowRequest(testUserId, request);
        assertTrue(secondResult.isAllowed(), "두 번째 요청은 허용되어야 합니다");
        log.info("두 번째 요청 성공 - remaining: {}", secondResult.getRemainingRequests());
        
        // 세 번째 요청 - 이제 거부되어야 함 (용량 초과)
        RateLimitResult thirdResult = strictRateLimiter.allowRequest(testUserId, request);
        assertFalse(thirdResult.isAllowed(), "세 번째 요청은 거부되어야 합니다 (용량 초과)");
        assertEquals("leaky-bucket", thirdResult.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(thirdResult.getRetryAfterSeconds() > 0, "재시도 시간이 설정되어야 합니다");
        
        log.info("Leaky Bucket 실패 테스트 완료 - 요청 거부됨, retryAfter: {}초", 
                thirdResult.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("누출 기능 테스트 - 시간이 지나면 요청이 다시 허용되어야 함")
    void testLeakingBehavior() throws InterruptedException {
        log.info("=== Leaky Bucket 누출 기능 테스트 시작 ===");
        
        // 버킷을 가득 채움
        RateLimitResult firstResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(firstResult.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        
        // 즉시 두 번째 요청 - 거부되거나 허용될 수 있음 (매우 짧은 시간 동안 누출 가능)
        RateLimitResult secondResult = rateLimiter.allowRequest(testUserId, request);
        log.info("두 번째 요청 결과: allowed={}, remaining={}", secondResult.isAllowed(), secondResult.getRemainingRequests());
        
        // 잠시 대기 (누출이 일어나도록)
        log.info("누출을 위해 대기 중...");
        TimeUnit.MILLISECONDS.sleep(3000); // 3초 대기
        
        // 세 번째 요청 - 누출로 인해 허용되어야 함
        RateLimitResult thirdResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(thirdResult.isAllowed(), "누출 후에는 요청이 허용되어야 합니다");
        
        log.info("Leaky Bucket 누출 테스트 완료 - 누출 후 요청 허용됨");
    }

    @Test
    @DisplayName("사용자별 격리 테스트 - 다른 사용자는 독립적으로 동작해야 함")
    void testUserIsolation() {
        log.info("=== Leaky Bucket 사용자별 격리 테스트 시작 ===");
        
        Long user1 = 100L;
        Long user2 = 200L;
        
        // 사용자1의 버킷을 가득 채움
        RateLimitResult user1FirstRequest = rateLimiter.allowRequest(user1, request);
        assertTrue(user1FirstRequest.isAllowed(), "사용자1의 첫 번째 요청은 허용되어야 합니다");
        
        // 사용자1의 두 번째 요청 거부 확인
        RateLimitResult user1SecondRequest = rateLimiter.allowRequest(user1, request);
        assertFalse(user1SecondRequest.isAllowed(), "사용자1의 두 번째 요청은 거부되어야 합니다");
        
        // 사용자2는 여전히 요청 가능해야 함
        RateLimitResult user2Request = rateLimiter.allowRequest(user2, request);
        assertTrue(user2Request.isAllowed(), "사용자2의 요청은 허용되어야 합니다 (독립적 격리)");
        
        log.info("Leaky Bucket 사용자 격리 테스트 완료 - 사용자별 독립적 동작 확인");
    }

    @Test
    @DisplayName("리셋 기능 테스트 - 리셋 후 요청이 다시 허용되어야 함")
    void testResetFunction() {
        log.info("=== Leaky Bucket 리셋 기능 테스트 시작 ===");
        
        // 버킷을 가득 채움
        rateLimiter.allowRequest(testUserId, request);
        RateLimitResult beforeReset = rateLimiter.allowRequest(testUserId, request);
        assertFalse(beforeReset.isAllowed(), "리셋 전에는 요청이 거부되어야 합니다");
        
        // 리셋 실행
        rateLimiter.reset(testUserId);
        log.info("사용자 {}의 Leaky Bucket 리셋 완료", testUserId);
        
        // 리셋 후 요청 허용 확인
        RateLimitResult afterReset = rateLimiter.allowRequest(testUserId, request);
        assertTrue(afterReset.isAllowed(), "리셋 후에는 요청이 허용되어야 합니다");
        
        log.info("Leaky Bucket 리셋 테스트 완료 - 리셋 후 정상 동작 확인");
    }

    @Test
    @DisplayName("통계 기능 테스트 - 정확한 통계 정보를 반환해야 함")
    void testStatsFunction() {
        log.info("=== Leaky Bucket 통계 기능 테스트 시작 ===");
        
        // 요청 실행
        rateLimiter.allowRequest(testUserId, request);
        
        // 통계 조회
        var stats = rateLimiter.getStats(testUserId);
        
        assertNotNull(stats, "통계 정보가 null이 아니어야 합니다");
        assertEquals("leaky-bucket", stats.get("algorithm"), "알고리즘 이름이 올바르게 설정되어야 합니다");
        assertEquals(testUserId, stats.get("userId"), "사용자 ID가 올바르게 설정되어야 합니다");
        assertTrue(stats.containsKey("currentRequests"), "현재 요청 수 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("capacity"), "용량 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("leakRate"), "누출 비율 정보가 포함되어야 합니다");
        
        log.info("Leaky Bucket 통계 테스트 완료 - stats: {}", stats);
    }
}
