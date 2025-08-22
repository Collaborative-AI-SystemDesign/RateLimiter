package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimitResult;
import com.example.demo.ratelimiter.core.RateLimiter;
import com.example.demo.ratelimiter.model.FixedWindowState;
import com.example.demo.ratelimiter.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class FixedWindowRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<Long, FixedWindowState> windows = new ConcurrentHashMap<>();
    private final int defaultLimit;
    private final long defaultWindowSizeSeconds;


    public FixedWindowRateLimiter() {
        this(100, 60); // 기본: 60초 윈도우에 100개 요청
    }

    /**
     * 사용자 정의 설정으로 FixedWindowRateLimiter 생성
     *
     * @param limit 윈도우 내 최대 요청 수
     * @param windowSizeSeconds 윈도우 크기 (초)
     */
    public FixedWindowRateLimiter(int limit, long windowSizeSeconds) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (windowSizeSeconds <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        
        this.defaultLimit = limit;
        this.defaultWindowSizeSeconds = windowSizeSeconds;
        
        log.info("FixedWindowRateLimiter initialized - limit: {}, windowSize: {}s",
                limit, windowSizeSeconds);
    }
    
    @Override
    public RateLimitResult allowRequest(Long userId, HttpServletRequest request) {
        long currentTime = TimeUtil.currentTimeMillis();
        
        // 사용자별 윈도우 상태 가져오기 또는 생성
        FixedWindowState window = windows.computeIfAbsent(userId,
                k -> FixedWindowState.createFixedWindow(defaultLimit, defaultWindowSizeSeconds));
        
        // 동기화된 블록에서 윈도우 확인 및 요청 처리
        synchronized (window) {
            // 윈도우가 만료되었는지 확인하고 리셋
            checkAndResetWindow(window, currentTime);
            
            // 요청 한도 확인
            if (window.getRequestCount() < defaultLimit) {
                // 요청 허용 및 카운트 증가
                window.setRequestCount(window.getRequestCount() + 1);
                
                long remainingRequests = defaultLimit - window.getRequestCount();
                long resetTime = window.getWindowStartTime() + window.getWindowSizeMillis();
                
                log.debug("Request allowed for user: {} - count: {}/{}", 
                        userId, window.getRequestCount(), defaultLimit);
                
                return RateLimitResult.allowed(remainingRequests, resetTime, getAlgorithmName());
            } else {
                // 요청 거부 (윈도우 한도 초과)
                long resetTime = window.getWindowStartTime() + window.getWindowSizeMillis();
                long retryAfter = TimeUtil.calculateRetryAfterSeconds(resetTime);
                
                log.debug("Request rejected for user: {} - limit exceeded {}/{}, retry after: {}s", 
                        userId, window.getRequestCount(), defaultLimit, retryAfter);
                
                return RateLimitResult.rejected(resetTime, getAlgorithmName(), retryAfter);
            }
        }
    }
    
    // 윈도우 만료 확인 및 리셋
    private void checkAndResetWindow(FixedWindowState window, long currentTime) {
        long windowEndTime = window.getWindowStartTime() + window.getWindowSizeMillis();
        
        if (currentTime >= windowEndTime) {
            // 새 윈도우 시작
            long windowSizeMillis = window.getWindowSizeMillis();
            long newWindowStartTime = (currentTime / windowSizeMillis) * windowSizeMillis;
            
            window.setWindowStartTime(newWindowStartTime);
            window.setRequestCount(0);
            
            log.trace("Window reset for current time: {} - new window start: {}", 
                    currentTime, newWindowStartTime);
        }
    }
    
    @Override
    public void reset(Long userId) {
        windows.remove(userId);
        log.debug("Reset window for user: {}", userId);
    }
    
    @Override
    public Map<String, Object> getStats(Long userId) {
        FixedWindowState window = windows.get(userId);
        Map<String, Object> stats = new HashMap<>();
        
        if (window != null) {
            synchronized (window) {
                // 현재 시간 기준으로 윈도우 상태 업데이트
                checkAndResetWindow(window, TimeUtil.currentTimeMillis());
                
                stats.put("algorithm", getAlgorithmName());
                stats.put("userId", userId);
                stats.put("currentRequests", window.getRequestCount());
                stats.put("limit", window.getLimit());
                stats.put("remainingRequests", defaultLimit - window.getRequestCount());
                stats.put("windowStartTime", window.getWindowStartTime());
                stats.put("windowStartTimeFormatted", TimeUtil.formatTimestamp(window.getWindowStartTime()));
                stats.put("windowSizeSeconds", window.getWindowSizeMillis() / 1000);
                stats.put("windowEndTime", window.getWindowStartTime() + window.getWindowSizeMillis());
                stats.put("windowEndTimeFormatted", TimeUtil.formatTimestamp(window.getWindowStartTime() + window.getWindowSizeMillis()));
            }
        } else {
            stats.put("algorithm", getAlgorithmName());
            stats.put("userId", userId);
            stats.put("status", "No window found");
        }
        
        return stats;
    }
    
    @Override
    public String getAlgorithmName() {
        return "fixed-window";
    }
    
    // 모든 윈도우 정리
    public void clearAllWindows() {
        windows.clear();
        log.debug("All windows cleared");
    }
    
    // 만료된 윈도우 정리 (메모리 관리 용도)
    public int cleanupExpiredWindows() {
        long currentTime = TimeUtil.currentTimeMillis();
        final int[] removedCount = {0};
        
        windows.entrySet().removeIf(entry -> {
            FixedWindowState window = entry.getValue();
            boolean isExpired = (currentTime - window.getWindowStartTime()) > window.getWindowSizeMillis() * 2; // 윈도우 크기의 2배 지난 경우
            if (isExpired) {
                removedCount[0]++;
            }
            return isExpired;
        });
        
        if (removedCount[0] > 0) {
            log.info("Cleaned up {} expired windows", removedCount[0]);
        }
        
        return removedCount[0];
    }
}
