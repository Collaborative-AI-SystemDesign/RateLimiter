package com.example.demo.ratelimiter.algo;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sliding Window Log Algorithm
 * 
 * 동작 원리:
 * - 각 요청의 타임스탬프를 로그에 저장
 * - 윈도우 크기만큼의 과거 요청들을 추적
 * - 새 요청 시 오래된 로그를 제거하고 현재 요청 수 확인
 * 
 * 장점:
 * - 정확한 제한이 가능 (윈도우 경계 문제 없음)
 * - 균등한 요청 분산 보장
 * - 버스트 트래픽을 효과적으로 제어
 * 
 * 단점:
 * - 메모리 사용량이 많음 (모든 요청 타임스탬프 저장)
 * - 성능이 상대적으로 떨어짐
 * - 확장성에 제한이 있음
 */
@Component
public class SlidingWindowLogLimiter implements RateLimiter {
    
    private final ConcurrentHashMap<String, SlidingWindowLog> logs = new ConcurrentHashMap<>();
    private final RateLimitConfig defaultConfig;
    
    public SlidingWindowLogLimiter() {
        this.defaultConfig = RateLimitConfig.forWindow(10, 60000); // 1분당 10개 요청
    }
    
    public SlidingWindowLogLimiter(RateLimitConfig config) {
        this.defaultConfig = config;
    }
    
    @Override
    public RateLimitResult tryAcquire(String key) {
        SlidingWindowLog log = logs.computeIfAbsent(key, k -> new SlidingWindowLog(defaultConfig));
        return log.tryAdd();
    }
    
    @Override
    public RateLimitResult getStatus(String key) {
        SlidingWindowLog log = logs.get(key);
        if (log == null) {
            return RateLimitResult.allowed(
                defaultConfig.getCapacity(), 
                System.currentTimeMillis() + defaultConfig.getWindowSizeMs(), 
                "SLIDING_WINDOW_LOG"
            );
        }
        return log.getStatus();
    }
    
    @Override
    public void reset(String key) {
        logs.remove(key);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        SlidingWindowLog log = logs.computeIfAbsent(key, k -> new SlidingWindowLog(config));
        return log.tryAdd();
    }
    
    /**
     * Sliding Window Log 내부 구현 클래스
     */
    private static class SlidingWindowLog {
        private final ConcurrentLinkedQueue<Long> requestLog;
        private final RateLimitConfig config;
        
        public SlidingWindowLog(RateLimitConfig config) {
            this.config = config;
            this.requestLog = new ConcurrentLinkedQueue<>();
        }
        
        public synchronized RateLimitResult tryAdd() {
            long now = System.currentTimeMillis();
            cleanupOldRequests(now);
            
            if (requestLog.size() < config.getCapacity()) {
                requestLog.offer(now);
                return RateLimitResult.allowed(
                    config.getCapacity() - requestLog.size(),
                    getNextResetTime(now),
                    "SLIDING_WINDOW_LOG",
                    "Request added to sliding window"
                );
            }
            
            return RateLimitResult.denied(
                0, 
                getNextResetTime(now), 
                "SLIDING_WINDOW_LOG",
                "Sliding window limit exceeded"
            );
        }
        
        public synchronized RateLimitResult getStatus() {
            long now = System.currentTimeMillis();
            cleanupOldRequests(now);
            
            return RateLimitResult.allowed(
                config.getCapacity() - requestLog.size(),
                getNextResetTime(now),
                "SLIDING_WINDOW_LOG",
                "Current sliding window status"
            );
        }
        
        /**
         * 윈도우 범위를 벗어난 오래된 요청들을 제거
         */
        private void cleanupOldRequests(long now) {
            long windowStart = now - config.getWindowSizeMs();
            
            // 윈도우 시작 시간보다 이전의 모든 요청 제거
            while (!requestLog.isEmpty() && requestLog.peek() < windowStart) {
                requestLog.poll();
            }
        }
        
        /**
         * 다음 리셋 시간 계산
         * 가장 오래된 요청이 윈도우를 벗어나는 시간
         */
        private long getNextResetTime(long now) {
            if (requestLog.isEmpty()) {
                return now + config.getWindowSizeMs();
            }
            // 가장 오래된 요청 + 윈도우 크기 = 해당 요청이 만료되는 시간
            return requestLog.peek() + config.getWindowSizeMs();
        }
    }
} 