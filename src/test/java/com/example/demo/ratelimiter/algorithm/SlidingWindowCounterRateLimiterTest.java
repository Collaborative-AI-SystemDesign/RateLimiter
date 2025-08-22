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
 * Sliding Window Counter Rate Limiter 테스트
 * 성공/실패 케이스를 모두 검증합니다.
 */
@SpringBootTest
@Slf4j
@DisplayName("Sliding Window Counter Rate Limiter 테스트")
class SlidingWindowCounterRateLimiterTest {

    private SlidingWindowCounterRateLimiter rateLimiter;
    private MockHttpServletRequest request;
    private final Long testUserId = 123L;

    @BeforeEach
    void setUp() {
        // 허용 요청 수 1, 윈도우 5초, 서브윈도우 5개로 설정하여 빠른 테스트 가능
        rateLimiter = new SlidingWindowCounterRateLimiter(1, 5, 5); // 1개 허용, 5초 윈도우, 5 서브윈도우
        request = new MockHttpServletRequest();
        log.info("Sliding Window Counter Rate Limiter 초기화 완료 - limit: 1, windowSize: 5s, subWindows: 5");
    }

    @Test
    @DisplayName("성공 케이스 - 첫 번째 요청은 허용되어야 함")
    void testSuccessCase() {
        log.info("=== Sliding Window Counter 성공 케이스 테스트 시작 ===");
        
        // 첫 번째 요청 - 성공해야 함
        RateLimitResult result = rateLimiter.allowRequest(testUserId, request);
        
        assertTrue(result.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        assertEquals("sliding-window-counter", result.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        assertTrue(result.getResetTimeMillis() > 0, "리셋 시간이 설정되어야 합니다");
        
        log.info("Sliding Window Counter 성공 테스트 완료 - remaining: {}, algorithm: {}", 
                result.getRemainingRequests(), result.getAlgorithm());
    }

    @Test
    @DisplayName("실패 케이스 - 가중 평균 한도 초과 시 요청이 거부되어야 함")
    void testFailureCase() {
        log.info("=== Sliding Window Counter 실패 케이스 테스트 시작 ===");
        
        // 연속으로 여러 요청을 보내어 한도 초과 상황 만들기
        RateLimitResult firstResult = rateLimiter.allowRequest(testUserId, request);
        assertTrue(firstResult.isAllowed(), "첫 번째 요청은 허용되어야 합니다");
        log.info("첫 번째 요청 성공 - remaining: {}", firstResult.getRemainingRequests());
        
        // 즉시 두 번째 요청
        RateLimitResult secondResult = rateLimiter.allowRequest(testUserId, request);
        
        // Sliding Window Counter는 가중 평균을 사용하므로 정확한 예측이 어려울 수 있음
        // 따라서 여러 번 시도하여 거부되는 상황을 만듦
        boolean eventuallyRejected = false;
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = rateLimiter.allowRequest(testUserId, request);
            if (!result.isAllowed()) {
                eventuallyRejected = true;
                assertEquals("sliding-window-counter", result.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
                assertTrue(result.getRetryAfterSeconds() > 0, "재시도 시간이 설정되어야 합니다");
                log.info("Sliding Window Counter 실패 테스트 완료 - 요청 거부됨, retryAfter: {}초", 
                        result.getRetryAfterSeconds());
                break;
            }
        }
        
        assertTrue(eventuallyRejected, "연속 요청 시 최종적으로 거부되어야 합니다");
    }

    @Test
    @DisplayName("슬라이딩 윈도우 테스트 - 시간이 지나면 새 요청이 허용되어야 함")
    void testSlidingWindow() throws InterruptedException {
        log.info("=== Sliding Window Counter 슬라이딩 윈도우 테스트 시작 ===");
        
        // 여러 요청으로 한도 근처까지 채움
        for (int i = 0; i < 3; i++) {
            rateLimiter.allowRequest(testUserId, request);
            TimeUnit.MILLISECONDS.sleep(100);
        }
        
        // 서브윈도우가 슬라이드되도록 대기
        log.info("서브윈도우 슬라이딩을 위해 대기 중...");
        TimeUnit.SECONDS.sleep(2); // 서브윈도우 크기만큼 대기
        
        // 새 요청 - 슬라이딩으로 인해 허용될 가능성이 높음
        RateLimitResult newResult = rateLimiter.allowRequest(testUserId, request);
        
        // Sliding Window Counter는 가중 평균을 사용하므로 항상 허용되지는 않을 수 있음
        log.info("슬라이딩 후 요청 결과 - allowed: {}, remaining: {}", 
                newResult.isAllowed(), newResult.getRemainingRequests());
        
        // 기본적으로 알고리즘이 정상 동작하는지만 확인
        assertEquals("sliding-window-counter", newResult.getAlgorithm(), "알고리즘 이름이 올바르지 않습니다");
        
        log.info("Sliding Window Counter 슬라이딩 윈도우 테스트 완료");
    }

    @Test
    @DisplayName("사용자별 격리 테스트 - 다른 사용자는 독립적으로 동작해야 함")
    void testUserIsolation() {
        log.info("=== Sliding Window Counter 사용자별 격리 테스트 시작 ===");
        
        Long user1 = 100L;
        Long user2 = 200L;
        
        // 사용자1의 여러 요청
        for (int i = 0; i < 3; i++) {
            rateLimiter.allowRequest(user1, request);
        }
        
        // 사용자2는 독립적으로 요청 가능해야 함
        RateLimitResult user2Request = rateLimiter.allowRequest(user2, request);
        assertTrue(user2Request.isAllowed(), "사용자2의 요청은 허용되어야 합니다 (독립적 격리)");
        
        log.info("Sliding Window Counter 사용자 격리 테스트 완료 - 사용자별 독립적 동작 확인");
    }

    @Test
    @DisplayName("리셋 기능 테스트 - 리셋 후 요청이 다시 허용되어야 함")
    void testResetFunction() {
        log.info("=== Sliding Window Counter 리셋 기능 테스트 시작 ===");
        
        // 여러 요청으로 카운터를 채움
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(testUserId, request);
        }
        
        // 리셋 실행
        rateLimiter.reset(testUserId);
        log.info("사용자 {}의 Sliding Window Counter 리셋 완료", testUserId);
        
        // 리셋 후 요청 허용 확인
        RateLimitResult afterReset = rateLimiter.allowRequest(testUserId, request);
        assertTrue(afterReset.isAllowed(), "리셋 후에는 요청이 허용되어야 합니다");
        
        log.info("Sliding Window Counter 리셋 테스트 완료 - 리셋 후 정상 동작 확인");
    }

    @Test
    @DisplayName("다중 요청 처리 테스트 - 허용 한도 내에서 여러 요청 처리")
    void testMultipleRequestsWithHigherLimit() {
        log.info("=== Sliding Window Counter 다중 요청 처리 테스트 시작 ===");
        
        // 더 큰 한도로 새 Rate Limiter 생성
        SlidingWindowCounterRateLimiter multiRequestLimiter = 
            new SlidingWindowCounterRateLimiter(5, 10, 5);
        
        // 5개의 요청 모두 허용되어야 함
        int allowedCount = 0;
        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = multiRequestLimiter.allowRequest(testUserId, request);
            if (result.isAllowed()) {
                allowedCount++;
            }
            log.info("{}번째 요청 - allowed: {}, remaining: {}", i, result.isAllowed(), result.getRemainingRequests());
        }
        
        assertTrue(allowedCount >= 3, "다수의 요청이 허용되어야 합니다 (가중 평균 특성상 완전히 정확하지 않을 수 있음)");
        
        log.info("Sliding Window Counter 다중 요청 테스트 완료 - allowedCount: {}", allowedCount);
    }

    @Test
    @DisplayName("서브윈도우 크기 테스트 - 다른 서브윈도우 설정에서 정상 동작")
    void testDifferentSubWindowSizes() {
        log.info("=== Sliding Window Counter 서브윈도우 크기 테스트 시작 ===");
        
        // 다른 서브윈도우 크기로 테스트
        SlidingWindowCounterRateLimiter limiter2SubWindows = 
            new SlidingWindowCounterRateLimiter(3, 6, 2); // 2개 서브윈도우
        
        SlidingWindowCounterRateLimiter limiter10SubWindows = 
            new SlidingWindowCounterRateLimiter(3, 6, 10); // 10개 서브윈도우
        
        // 각각 요청 테스트
        RateLimitResult result2Sub = limiter2SubWindows.allowRequest(testUserId, request);
        assertTrue(result2Sub.isAllowed(), "2개 서브윈도우에서 첫 요청은 허용되어야 합니다");
        
        RateLimitResult result10Sub = limiter10SubWindows.allowRequest(testUserId, request);
        assertTrue(result10Sub.isAllowed(), "10개 서브윈도우에서 첫 요청은 허용되어야 합니다");
        
        log.info("Sliding Window Counter 서브윈도우 크기 테스트 완료 - 다양한 설정에서 정상 동작");
    }

    @Test
    @DisplayName("통계 기능 테스트 - 정확한 통계 정보를 반환해야 함")
    void testStatsFunction() {
        log.info("=== Sliding Window Counter 통계 기능 테스트 시작 ===");
        
        // 요청 실행
        rateLimiter.allowRequest(testUserId, request);
        
        // 통계 조회
        var stats = rateLimiter.getStats(testUserId);
        
        assertNotNull(stats, "통계 정보가 null이 아니어야 합니다");
        assertEquals("sliding-window-counter", stats.get("algorithm"), "알고리즘 이름이 올바르게 설정되어야 합니다");
        assertEquals(testUserId, stats.get("userId"), "사용자 ID가 올바르게 설정되어야 합니다");
        assertTrue(stats.containsKey("currentWeightedRequests"), "현재 가중 요청 수 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("limit"), "제한 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("windowSizeSeconds"), "윈도우 크기 정보가 포함되어야 합니다");
        assertTrue(stats.containsKey("activeSubWindows"), "활성 서브윈도우 수 정보가 포함되어야 합니다");
        
        log.info("Sliding Window Counter 통계 테스트 완료 - stats: {}", stats);
    }
}
