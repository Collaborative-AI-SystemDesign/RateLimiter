package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimitResult;
import com.example.demo.ratelimiter.core.RateLimiter;
import com.example.demo.ratelimiter.model.SlidingWindowLogState;
import com.example.demo.ratelimiter.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<Long, SlidingWindowLogState> logs = new ConcurrentHashMap<>();
    private final int defaultLimit;
    private final long defaultWindowSizeSeconds;

    public SlidingWindowLogRateLimiter() {
        this(100, 60); // 기본: 60초 윈도우에 100개 요청
    }

    /**
     * 사용자 정의 설정으로 SlidingWindowLogRateLimiter 생성
     *
     * @param limit 윈도우 내 최대 요청 수
     * @param windowSizeSeconds 윈도우 크기 (초)
     */
    public SlidingWindowLogRateLimiter(int limit, long windowSizeSeconds) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (windowSizeSeconds <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        
        this.defaultLimit = limit;
        this.defaultWindowSizeSeconds = windowSizeSeconds;
        
        log.info("SlidingWindowLogRateLimiter initialized - limit: {}, windowSize: {}s",
                limit, windowSizeSeconds);
    }
    
    @Override
    public RateLimitResult allowRequest(Long userId, HttpServletRequest request) {
        long currentTime = TimeUtil.currentTimeMillis();
        
        // 사용자별 로그 상태 가져오기 또는 생성
        SlidingWindowLogState logState = logs.computeIfAbsent(userId,
                k -> SlidingWindowLogState.createSlidingWindowLog(defaultLimit, defaultWindowSizeSeconds));
        
        // 동기화된 블록에서 로그 정리 및 요청 처리
        synchronized (logState) {
            // 윈도우 밖의 오래된 요청 제거
            cleanupExpiredRequests(logState, currentTime);
            
            // 현재 윈도우 내 요청 수 확인
            int currentRequestCount = logState.getRequestLog().size();
            
            if (currentRequestCount < defaultLimit) {
                // 요청 허용 및 로그 추가
                logState.getRequestLog().offer(currentTime);
                
                long remainingRequests = defaultLimit - (currentRequestCount + 1);
                long resetTime = calculateResetTime(logState, currentTime);
                
                log.debug("Request allowed for user: {} - count: {}/{}", 
                        userId, currentRequestCount + 1, defaultLimit);
                
                return RateLimitResult.allowed(remainingRequests, resetTime, getAlgorithmName());
            } else {
                // 요청 거부 (윈도우 한도 초과)
                long resetTime = calculateResetTime(logState, currentTime);
                long retryAfter = TimeUtil.calculateRetryAfterSeconds(resetTime);
                
                log.debug("Request rejected for user: {} - limit exceeded {}/{}, retry after: {}s", 
                        userId, currentRequestCount, defaultLimit, retryAfter);
                
                return RateLimitResult.rejected(resetTime, getAlgorithmName(), retryAfter);
            }
        }
    }
    
    //윈도우 밖의 만료된 요청 제거
    private void cleanupExpiredRequests(SlidingWindowLogState logState, long currentTime) {
        long windowStartTime = currentTime - logState.getWindowSizeMillis();
        
        // 윈도우 시작 시간 이전의 요청들 제거
        while (!logState.getRequestLog().isEmpty() && 
               logState.getRequestLog().peek() < windowStartTime) {
            logState.getRequestLog().poll();
        }
        
        log.trace("Cleanup completed - remaining requests in window: {}", 
                logState.getRequestLog().size());
    }
    
    //다음 요청이 가능한 시간 계산
    private long calculateResetTime(SlidingWindowLogState logState, long currentTime) {
        if (logState.getRequestLog().isEmpty()) {
            return currentTime; // 로그가 비어있으면 즉시 가능
        }
        
        // 가장 오래된 요청이 윈도우에서 빠지는 시간
        Long oldestRequest = logState.getRequestLog().peek();
        if (oldestRequest != null) {
            return oldestRequest + logState.getWindowSizeMillis();
        }
        
        return currentTime + logState.getWindowSizeMillis();
    }
    
    @Override
    public void reset(Long userId) {
        logs.remove(userId);
        log.debug("Reset log for user: {}", userId);
    }
    
    @Override
    public Map<String, Object> getStats(Long userId) {
        SlidingWindowLogState logState = logs.get(userId);
        Map<String, Object> stats = new HashMap<>();
        
        if (logState != null) {
            synchronized (logState) {
                // 현재 시간 기준으로 로그 정리
                cleanupExpiredRequests(logState, TimeUtil.currentTimeMillis());
                
                int currentRequestCount = logState.getRequestLog().size();
                
                stats.put("algorithm", getAlgorithmName());
                stats.put("userId", userId);
                stats.put("currentRequests", currentRequestCount);
                stats.put("limit", logState.getLimit());
                stats.put("remainingRequests", defaultLimit - currentRequestCount);
                stats.put("windowSizeSeconds", logState.getWindowSizeMillis() / 1000);
                
                // 요청 로그 세부 정보 (디버깅용)
                if (!logState.getRequestLog().isEmpty()) {
                    stats.put("oldestRequestTime", logState.getRequestLog().peek());
                    stats.put("oldestRequestTimeFormatted", TimeUtil.formatTimestamp(logState.getRequestLog().peek()));
                    stats.put("newestRequestTime", ((java.util.LinkedList<Long>) logState.getRequestLog()).peekLast());
                    if (((java.util.LinkedList<Long>) logState.getRequestLog()).peekLast() != null) {
                        stats.put("newestRequestTimeFormatted", TimeUtil.formatTimestamp(((java.util.LinkedList<Long>) logState.getRequestLog()).peekLast()));
                    }
                }
            }
        } else {
            stats.put("algorithm", getAlgorithmName());
            stats.put("userId", userId);
            stats.put("status", "No log found");
        }
        
        return stats;
    }
    
    @Override
    public String getAlgorithmName() {
        return "sliding-window-log";
    }
    
    //모든 로그 정리 (테스트 또는 메모리 관리 용도)
    public void clearAllLogs() {
        logs.clear();
        log.debug("All logs cleared");
    }
    
    //비활성 사용자 로그 정리 (메모리 관리 용도)
    public int cleanupInactiveLogs(long inactiveThresholdMillis) {
        long currentTime = TimeUtil.currentTimeMillis();
        final int[] removedCount = {0};
        
        logs.entrySet().removeIf(entry -> {
            SlidingWindowLogState logState = entry.getValue();
            synchronized (logState) {
                cleanupExpiredRequests(logState, currentTime);
                
                // 로그가 비어있거나 마지막 요청이 임계값보다 오래된 경우
                boolean isInactive = logState.getRequestLog().isEmpty();
                if (!isInactive && !logState.getRequestLog().isEmpty()) {
                    Long lastRequest = ((java.util.LinkedList<Long>) logState.getRequestLog()).peekLast();
                    isInactive = lastRequest != null && (currentTime - lastRequest) > inactiveThresholdMillis;
                }
                
                if (isInactive) {
                    removedCount[0]++;
                }
                return isInactive;
            }
        });
        
        if (removedCount[0] > 0) {
            log.info("Cleaned up {} inactive logs", removedCount[0]);
        }
        
        return removedCount[0];
    }
}
