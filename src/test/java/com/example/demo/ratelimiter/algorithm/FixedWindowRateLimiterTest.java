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
 * Fixed Window Rate Limiter 테스트
 * 성공/실패 케이스를 모두 검증합니다.
 */
@SpringBootTest
@Slf4j
@DisplayName("Fixed Window Rate Limiter 테스트")
class FixedWindowRateLimiterTest {

    private FixedWindowRateLimiter rateLimiter;
    private MockHttpServletRequest request;
    private final Long testUserId = 123L;

    @BeforeEach
    void setUp() {
        // 허용 요청 수 1, 윈도우 5초로 설정하여 빠른 테스트 가능
        rateLimiter = new FixedWindowRateLimiter(1, 5); // 1개 허용, 5초 윈도우
        request = new MockHttpServletRequest();
        log.info("Fixed Window Rate Limiter 초기화 완료 - limit: 1, windowSize: 5s");
    }

    @Test
    @DisplayName("성공 케이스 - 첫 번째 요청은 허용되어야 함")
    void testSuccessCase() {
        log.info("=== Fixed Window 성공 케이스 테스트 시작 ===");
        
        // 첫 번째 요청 - 성공해야 함
        RateLimitResult result = rateLimiter.allowRequest(testUserId, request);
        
        assertTrue(result.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        assertEquals(0, result.getRemainingRequests(), "남은 요청 수는 0이어야 합니다");
        assertEquals("fixed-window", result.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(result.getResetTimeMillis() > 0, "리셋 시간이 설정되어야 합니다");
        
        log.info("Fixed Window 성공 테스트 완료 - remaining: {}, algorithm: {}", 
                result.getRemainingRequests(), result.getAlgorithm());
    }

    @Test
    @DisplayName("실패 케이스 - 윈도우 한도 초과 시 요청이 거부되어야 함")
    void testFailureCase() {
        log.info("=== Fixed Window 실패 케이스 테스트 시작 ===");
        
        // 첫 번째 요청으로 윈도우를 가득 채움
        RateLimitResult firstResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(firstResult.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        log.info("첫 번째 요청 성공 - remaining: {}", firstResult.getRemainingRequests());
        
        // 두 번째 요청 - 실패해야 함 (윈도우 한도 초과)
        RateLimitResult secondResult = rateLimiter.allowRequest(testUserId, request);
        
        assertFalse(secondResult.isAllowed(), "두 번째 요청은 거부되어야 합니다 (윈도우 한도 초과)");
        assertEquals(0, secondResult.getRemainingRequests(), "남은 요청 수는 0이어야 합니다");
        assertEquals("fixed-window", secondResult.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(secondResult.getRetryAfterSeconds() > 0, "재시도 시간이 설정되어야 합니다");
        
        log.info("Fixed Window 실패 테스트 완료 - 요청 거부됨, retryAfter: {}초", 
                secondResult.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("윈도우 리셋 테스트 - 새 윈도우에서는 요청이 다시 허용되어야 함")
    void testWindowReset() throws InterruptedException {
        log.info("=== Fixed Window 윈도우 리셋 테스트 시작 ===");
        
        // 현재 윈도우를 가득 채움
        RateLimitResult firstResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(firstResult.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        
        // 즉시 두 번째 요청 - 거부됨
        RateLimitResult secondResult = rateLimiter.allowRequest(testUserId, request);
        assertFalse(secondResult.isAllowed(), "두 번째 요청은 거부되어야 합니다");
        
        // 윈도우 리셋을 위해 대기
        log.info("윈도우 리셋을 위해 대기 중...");
        TimeUnit.SECONDS.sleep(6); // 윈도우 크기보다 조금 더 대기
        
        // 새 윈도우에서 요청 - 허용되어야 함
        RateLimitResult thirdResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(thirdResult.isAllowed(), "새 윈도우에서는 요청이 허용되어야 합니다");
        
        log.info("Fixed Window 윈도우 리셋 테스트 완료 - 새 윈도우에서 요청 허용됨");
    }

    @Test
    @DisplayName("사용자별 격리 테스트 - 다른 사용자는 독립적으로 동작해야 함")
    void testUserIsolation() {
        log.info("=== Fixed Window 사용자별 격리 테스트 시작 ===");
        
        Long user1 = 100L;
        Long user2 = 200L;
        
        // 사용자1의 윈도우를 가득 채움
        RateLimitResult user1FirstRequest = rateLimiter.allowRequest(user1, request);
        assertTrue(user1FirstRequest.isAllowed(), "사용자1의 첫 번째 요청은 허용되어야 합니다");
        
        // 사용자1의 두 번째 요청 거부 확인
        RateLimitResult user1SecondRequest = rateLimiter.allowRequest(user1, request);
        assertFalse(user1SecondRequest.isAllowed(), "사용자1의 두 번째 요청은 거부되어야 합니다");
        
        // 사용자2는 여전히 요청 가능해야 함
        RateLimitResult user2Request = rateLimiter.allowRequest(user2, request);
        assertTrue(user2Request.isAllowed(), "사용자2의 요청은 허용되어야 합니다 (독립적 격리)");
        
        log.info("Fixed Window 사용자 격리 테스트 완료 - 사용자별 독립적 동작 확인");
    }

    @Test
    @DisplayName("리셋 기능 테스트 - 리셋 후 요청이 다시 허용되어야 함")
    void testResetFunction() {
        log.info("=== Fixed Window 리셋 기능 테스트 시작 ===");
        
        // 윈도우를 가득 채움
        rateLimiter.allowRequest(testUserId, request);
        RateLimitResult beforeReset = rateLimiter.allowRequest(testUserId, request);
        assertFalse(beforeReset.isAllowed(), "리셋 전에는 요청이 거부되어야 합니다");
        
        // 리셋 실행
        rateLimiter.reset(testUserId);
        log.info("사용자 {}의 Fixed Window 리셋 완료", testUserId);
        
        // 리셋 후 요청 허용 확인
        RateLimitResult afterReset = rateLimiter.allowRequest(testUserId, request);
        assertTrue(afterReset.isAllowed(), "리셋 후에는 요청이 허용되어야 합니다");
        
        log.info("Fixed Window 리셋 테스트 완료 - 리셋 후 정상 동작 확인");
    }

    @Test
    @DisplayName("다중 요청 처리 테스트 - 허용 한도 내에서 여러 요청 처리")
    void testMultipleRequestsWithHigherLimit() {
        log.info("=== Fixed Window 다중 요청 처리 테스트 시작 ===");
        
        // 더 큰 한도로 새 Rate Limiter 생성
        FixedWindowRateLimiter multiRequestLimiter = new FixedWindowRateLimiter(3, 10);
        
        // 3개의 요청 모두 허용되어야 함
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = multiRequestLimiter.allowRequest(testUserId, request);
            assertTrue(result.isAllowed(), i + "번째 요청은 허용되어야 합니다");
            assertEquals(3 - i, result.getRemainingRequests(), "남은 요청 수가 올바르지 않습니다");
            log.info("{}번째 요청 성공 - remaining: {}", i, result.getRemainingRequests());
        }
        
        // 4번째 요청은 거부되어야 함
        RateLimitResult fourthResult = multiRequestLimiter.allowRequest(testUserId, request);
        assertFalse(fourthResult.isAllowed(), "4번째 요청은 거부되어야 합니다");
        
        log.info("Fixed Window 다중 요청 테스트 완료 - 한도 내 요청 허용, 초과 요청 거부");
    }

    @Test
    @DisplayName("통계 기능 테스트 - 정확한 통계 정보를 반환해야 함")
    void testStatsFunction() {
        log.info("=== Fixed Window 통계 기능 테스트 시작 ===");
        
        // 요청 실행
        rateLimiter.allowRequest(testUserId, request);
        
        // 통계 조회
        var stats = rateLimiter.getStats(testUserId);
        
        assertNotNull(stats, "통계 정보가 null이 아니어야 합니다");
        assertEquals("fixed-window", stats.get("algorithm"), "알고리즘 이름이 올바르게 설정되어야 합니다");
        assertEquals(testUserId, stats.get("userId"), "사용자 ID가 올바르게 설정되어야 합니다");
        assertTrue(stats.containsKey("currentRequests"), "현재 요청 수 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("limit"), "제한 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("windowSizeSeconds"), "윈도우 크기 정보가 포함되어야 합니다");
        
        log.info("Fixed Window 통계 테스트 완료 - stats: {}", stats);
    }
}
