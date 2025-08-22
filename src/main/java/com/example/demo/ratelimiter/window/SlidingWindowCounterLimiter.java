package com.example.demo.ratelimiter.window;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding Window Counter Algorithm
 * 
 * 동작 원리:
 * - 현재 윈도우와 이전 윈도우의 카운터를 조합하여 계산
 * - 가중 평균을 사용하여 슬라이딩 윈도우 효과를 근사
 * - Fixed Window의 단점을 보완하면서 메모리 효율적
 * 
 * 장점:
 * - 메모리 효율적 (키당 3개의 값만 저장)
 * - Fixed Window의 버스트 문제 완화
 * - 성능이 우수함
 * - 구현이 비교적 간단
 * 
 * 단점:
 * - 근사치 계산 (완전히 정확하지 않음)
 * - 윈도우 경계에서 여전히 약간의 부정확성 존재
 * - 복잡한 계산 로직
 */
@Component
public class SlidingWindowCounterLimiter implements RateLimiter {
    
    private final ConcurrentHashMap<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();
    private final RateLimitConfig defaultConfig;
    
    public SlidingWindowCounterLimiter() {
        this.defaultConfig = RateLimitConfig.forWindow(10, 60000); // 1분당 10개 요청
    }
    
    public SlidingWindowCounterLimiter(RateLimitConfig config) {
        this.defaultConfig = config;
    }
    
    @Override
    public RateLimitResult tryAcquire(String key) {
        SlidingWindowCounter counter = counters.computeIfAbsent(key, k -> new SlidingWindowCounter(defaultConfig));
        return counter.tryIncrement();
    }
    
    @Override
    public RateLimitResult getStatus(String key) {
        SlidingWindowCounter counter = counters.get(key);
        if (counter == null) {
            return RateLimitResult.allowed(
                defaultConfig.getCapacity(), 
                System.currentTimeMillis() + defaultConfig.getWindowSizeMs(), 
                "SLIDING_WINDOW_COUNTER"
            );
        }
        return counter.getStatus();
    }
    
    @Override
    public void reset(String key) {
        counters.remove(key);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        SlidingWindowCounter counter = counters.computeIfAbsent(key, k -> new SlidingWindowCounter(config));
        return counter.tryIncrement();
    }
    
    /**
     * Sliding Window Counter 내부 구현 클래스
     */
    private static class SlidingWindowCounter {
        private final AtomicLong currentWindowStart;
        private final AtomicLong currentWindowCounter;
        private final AtomicLong previousWindowCounter;
        private final RateLimitConfig config;
        
        public SlidingWindowCounter(RateLimitConfig config) {
            this.config = config;
            long currentWindow = getCurrentWindow();
            this.currentWindowStart = new AtomicLong(currentWindow);
            this.currentWindowCounter = new AtomicLong(0);
            this.previousWindowCounter = new AtomicLong(0);
        }
        
        public RateLimitResult tryIncrement() {
            updateWindows();
            
            long estimatedCount = getEstimatedCount();
            if (estimatedCount < config.getCapacity()) {
                currentWindowCounter.incrementAndGet();
                return RateLimitResult.allowed(
                    config.getCapacity() - estimatedCount - 1,
                    getNextWindowStart(),
                    "SLIDING_WINDOW_COUNTER",
                    "Request counted in sliding window"
                );
            }
            
            return RateLimitResult.denied(
                0, 
                getNextWindowStart(), 
                "SLIDING_WINDOW_COUNTER",
                "Sliding window counter limit exceeded"
            );
        }
        
        public RateLimitResult getStatus() {
            updateWindows();
            
            long estimatedCount = getEstimatedCount();
            return RateLimitResult.allowed(
                config.getCapacity() - estimatedCount,
                getNextWindowStart(),
                "SLIDING_WINDOW_COUNTER",
                "Current sliding window counter status"
            );
        }
        
        /**
         * 윈도우 상태 업데이트
         * 새로운 윈도우로 이동했는지 확인하고 필요시 카운터 이동
         */
        private synchronized void updateWindows() {
            long currentWindow = getCurrentWindow();
            long windowStart = currentWindowStart.get();
            
            if (currentWindow > windowStart) {
                // 새로운 윈도우로 이동
                // 현재 윈도우의 카운터를 이전 윈도우로 이동
                previousWindowCounter.set(currentWindowCounter.get());
                currentWindowCounter.set(0);
                currentWindowStart.set(currentWindow);
            }
        }
        
        /**
         * 슬라이딩 윈도우의 추정 요청 수 계산
         * 가중 평균을 사용하여 현재 시점에서의 요청 수를 추정
         */
        private long getEstimatedCount() {
            long now = System.currentTimeMillis();
            long windowStart = currentWindowStart.get() * config.getWindowSizeMs();
            long timeIntoCurrentWindow = now - windowStart;
            
            // 현재 윈도우에서의 진행 비율 (0.0 ~ 1.0)
            double percentageOfCurrentWindow = (double) timeIntoCurrentWindow / config.getWindowSizeMs();
            
            // 가중 평균으로 추정 계산
            // 이전 윈도우의 기여도는 현재 윈도우 진행률에 반비례
            double estimatedPreviousCount = previousWindowCounter.get() * (1.0 - percentageOfCurrentWindow);
            long currentCount = currentWindowCounter.get();
            
            return (long) (estimatedPreviousCount + currentCount);
        }
        
        /**
         * 현재 윈도우 번호 계산
         */
        private long getCurrentWindow() {
            return System.currentTimeMillis() / config.getWindowSizeMs();
        }
        
        /**
         * 다음 윈도우 시작 시간 계산
         */
        private long getNextWindowStart() {
            return (currentWindowStart.get() + 1) * config.getWindowSizeMs();
        }
    }
} 