package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token Bucket Rate Limiter 테스트
 * 성공/실패 케이스를 모두 검증합니다.
 */
@SpringBootTest
@Slf4j
@DisplayName("Token Bucket Rate Limiter 테스트")
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;
    private MockHttpServletRequest request;
    private final Long testUserId = 123L;

    @BeforeEach
    void setUp() {
        // 허용 요청 수를 1로 제한하여 빠른 실패 테스트 가능
        rateLimiter = new TokenBucketRateLimiter(1, 0.1); // 1개 용량, 0.1 리필율
        request = new MockHttpServletRequest();
        log.info("Token Bucket Rate Limiter 초기화 완료 - capacity: 1, refillRate: 0.1");
    }

    @Test
    @DisplayName("성공 케이스 - 첫 번째 요청은 허용되어야 함")
    void testSuccessCase() {
        log.info("=== Token Bucket 성공 케이스 테스트 시작 ===");
        
        // 첫 번째 요청 - 성공해야 함
        RateLimitResult result = rateLimiter.allowRequest(testUserId, request);
        
        assertTrue(result.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        assertEquals(0, result.getRemainingRequests(), "남은 요청 수는 0이어야 합니다");
        assertEquals("token-bucket", result.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(result.getResetTimeMillis() > 0, "리셋 시간이 설정되어야 합니다");
        
        log.info("Token Bucket 성공 테스트 완료 - remaining: {}, algorithm: {}", 
                result.getRemainingRequests(), result.getAlgorithm());
    }

    @Test
    @DisplayName("실패 케이스 - 한도 초과 시 요청이 거부되어야 함")
    void testFailureCase() {
        log.info("=== Token Bucket 실패 케이스 테스트 시작 ===");
        
        // 첫 번째 요청으로 토큰 소진
        RateLimitResult firstResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(firstResult.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        log.info("첫 번째 요청 성공 - remaining: {}", firstResult.getRemainingRequests());
        
        // 두 번째 요청 - 실패해야 함 (토큰 부족)
        RateLimitResult secondResult = rateLimiter.allowRequest(testUserId, request);
        
        assertFalse(secondResult.isAllowed(), "두 번째 요청은 거부되어야 합니다");
        assertEquals(0, secondResult.getRemainingRequests(), "남은 요청 수는 0이어야 합니다");
        assertEquals("token-bucket", secondResult.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(secondResult.getRetryAfterSeconds() > 0, "재시도 시간이 설정되어야 합니다");
        
        log.info("Token Bucket 실패 테스트 완료 - 요청 거부됨, retryAfter: {}초", 
                secondResult.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("사용자별 격리 테스트 - 다른 사용자는 독립적으로 동작해야 함")
    void testUserIsolation() {
        log.info("=== Token Bucket 사용자별 격리 테스트 시작 ===");
        
        Long user1 = 100L;
        Long user2 = 200L;
        
        // 사용자1의 토큰 소진
        RateLimitResult user1FirstRequest = rateLimiter.allowRequest(user1, request);
        assertTrue(user1FirstRequest.isAllowed(), "사용자1의 첫 번째 요청은 허용되어야 합니다");
        
        // 사용자1의 두 번째 요청 거부 확인
        RateLimitResult user1SecondRequest = rateLimiter.allowRequest(user1, request);
        assertFalse(user1SecondRequest.isAllowed(), "사용자1의 두 번째 요청은 거부되어야 합니다");
        
        // 사용자2는 여전히 요청 가능해야 함
        RateLimitResult user2Request = rateLimiter.allowRequest(user2, request);
        assertTrue(user2Request.isAllowed(), "사용자2의 요청은 허용되어야 합니다 (독립적 격리)");
        
        log.info("Token Bucket 사용자 격리 테스트 완료 - 사용자별 독립적 동작 확인");
    }

    @Test
    @DisplayName("리셋 기능 테스트 - 리셋 후 요청이 다시 허용되어야 함")
    void testResetFunction() {
        log.info("=== Token Bucket 리셋 기능 테스트 시작 ===");
        
        // 토큰 소진
        rateLimiter.allowRequest(testUserId, request);
        RateLimitResult beforeReset = rateLimiter.allowRequest(testUserId, request);
        assertFalse(beforeReset.isAllowed(), "리셋 전에는 요청이 거부되어야 합니다");
        
        // 리셋 실행
        rateLimiter.reset(testUserId);
        log.info("사용자 {}의 Token Bucket 리셋 완료", testUserId);
        
        // 리셋 후 요청 허용 확인
        RateLimitResult afterReset = rateLimiter.allowRequest(testUserId, request);
        assertTrue(afterReset.isAllowed(), "리셋 후에는 요청이 허용되어야 합니다");
        
        log.info("Token Bucket 리셋 테스트 완료 - 리셋 후 정상 동작 확인");
    }

    @Test
    @DisplayName("통계 기능 테스트 - 정확한 통계 정보를 반환해야 함")
    void testStatsFunction() {
        log.info("=== Token Bucket 통계 기능 테스트 시작 ===");
        
        // 요청 실행
        rateLimiter.allowRequest(testUserId, request);
        
        // 통계 조회
        var stats = rateLimiter.getStats(testUserId);
        
        assertNotNull(stats, "통계 정보가 null이 아니어야 합니다");
        assertEquals("token-bucket", stats.get("algorithm"), "알고리즘 이름이 올바르게 설정되어야 합니다");
        assertEquals(testUserId, stats.get("userId"), "사용자 ID가 올바르게 설정되어야 합니다");
        assertTrue(stats.containsKey("currentTokens"), "현재 토큰 수 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("capacity"), "용량 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("refillRate"), "리필 비율 정보가 포함되어야 합니다");
        
        log.info("Token Bucket 통계 테스트 완료 - stats: {}", stats);
    }
}
