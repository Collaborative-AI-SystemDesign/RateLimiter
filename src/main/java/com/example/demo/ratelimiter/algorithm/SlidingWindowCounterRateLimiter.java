package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimitResult;
import com.example.demo.ratelimiter.core.RateLimiter;
import com.example.demo.ratelimiter.model.SlidingWindowCounterState;
import com.example.demo.ratelimiter.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<Long, SlidingWindowCounterState> counters = new ConcurrentHashMap<>();
    private final int defaultLimit;
    private final long defaultWindowSizeSeconds;
    private final int subWindowCount; // 서브 윈도우 개수

    public SlidingWindowCounterRateLimiter() {
        this(100, 60, 6); // 기본: 60초 윈도우에 100개 요청, 10초씩 6개 서브윈도우
    }

    /**
     * 사용자 정의 설정으로 SlidingWindowCounterRateLimiter 생성
     *
     * @param limit 윈도우 내 최대 요청 수
     * @param windowSizeSeconds 윈도우 크기 (초)
     * @param subWindowCount 서브 윈도우 개수
     */
    public SlidingWindowCounterRateLimiter(int limit, long windowSizeSeconds, int subWindowCount) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (windowSizeSeconds <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        if (subWindowCount <= 0) {
            throw new IllegalArgumentException("Sub window count must be positive");
        }
        
        this.defaultLimit = limit;
        this.defaultWindowSizeSeconds = windowSizeSeconds;
        this.subWindowCount = subWindowCount;
        
        log.info("SlidingWindowCounterRateLimiter initialized - limit: {}, windowSize: {}s, subWindows: {}",
                limit, windowSizeSeconds, subWindowCount);
    }
    
    @Override
    public RateLimitResult allowRequest(Long userId, HttpServletRequest request) {
        long currentTime = TimeUtil.currentTimeMillis();
        
        // 사용자별 카운터 상태 가져오기 또는 생성
        SlidingWindowCounterState counterState = counters.computeIfAbsent(userId,
                k -> SlidingWindowCounterState.createSlidingWindowCounter(defaultLimit, defaultWindowSizeSeconds, subWindowCount));
        
        // 동기화된 블록에서 카운터 정리 및 요청 처리
        synchronized (counterState) {
            // 만료된 서브 윈도우 정리
            cleanupExpiredSubWindows(counterState, currentTime);
            
            // 현재 슬라이딩 윈도우의 가중 요청 수 계산
            double currentRequestCount = calculateWeightedRequestCount(counterState, currentTime);
            
            if (currentRequestCount < defaultLimit) {
                // 요청 허용 및 현재 서브 윈도우 카운트 증가
                long currentSubWindowStart = getCurrentSubWindowStart(counterState, currentTime);
                counterState.getWindowCounts().merge(currentSubWindowStart, 1L, Long::sum);
                
                long remainingRequests = Math.max(0, (long) (defaultLimit - currentRequestCount - 1));
                long resetTime = calculateResetTime(counterState, currentTime);
                
                log.debug("Request allowed for user: {} - weighted count: {}/{}", 
                        userId, String.format("%.2f", currentRequestCount + 1), defaultLimit);
                
                return RateLimitResult.allowed(remainingRequests, resetTime, getAlgorithmName());
            } else {
                // 요청 거부 (윈도우 한도 초과)
                long resetTime = calculateResetTime(counterState, currentTime);
                long retryAfter = TimeUtil.calculateRetryAfterSeconds(resetTime);
                
                log.debug("Request rejected for user: {} - limit exceeded {}/{}, retry after: {}s", 
                        userId, String.format("%.2f", currentRequestCount), defaultLimit, retryAfter);
                
                return RateLimitResult.rejected(resetTime, getAlgorithmName(), retryAfter);
            }
        }
    }
    
    //만료된 서브 윈도우 정리
    private void cleanupExpiredSubWindows(SlidingWindowCounterState counterState, long currentTime) {
        long windowStartTime = currentTime - counterState.getWindowSizeMillis();
        
        counterState.getWindowCounts().entrySet().removeIf(entry -> 
            entry.getKey() < windowStartTime);
        
        log.trace("Cleanup completed - remaining sub-windows: {}", 
                counterState.getWindowCounts().size());
    }
    
    //슬라이딩 윈도우의 가중 요청 수 계산
    private double calculateWeightedRequestCount(SlidingWindowCounterState counterState, long currentTime) {
        long windowStartTime = currentTime - counterState.getWindowSizeMillis();
        double totalWeightedCount = 0.0;
        
        for (Map.Entry<Long, Long> entry : counterState.getWindowCounts().entrySet()) {
            long subWindowStart = entry.getKey();
            long count = entry.getValue();
            
            if (subWindowStart >= windowStartTime) {
                // 서브 윈도우가 현재 슬라이딩 윈도우에 포함되는 비율 계산
                double weight = calculateSubWindowWeight(counterState, subWindowStart, currentTime);
                totalWeightedCount += count * weight;
            }
        }
        
        return totalWeightedCount;
    }
    
    //서브 윈도우의 가중치 계산
    private double calculateSubWindowWeight(SlidingWindowCounterState counterState, long subWindowStart, long currentTime) {
        long windowStartTime = currentTime - counterState.getWindowSizeMillis();
        long subWindowEnd = subWindowStart + counterState.getSubWindowSizeMillis();
        
        // 서브 윈도우가 완전히 슬라이딩 윈도우 내에 있는 경우
        if (subWindowStart >= windowStartTime && subWindowEnd <= currentTime) {
            return 1.0;
        }
        
        // 서브 윈도우가 부분적으로 겹치는 경우
        long overlapStart = Math.max(subWindowStart, windowStartTime);
        long overlapEnd = Math.min(subWindowEnd, currentTime);
        long overlapDuration = Math.max(0, overlapEnd - overlapStart);
        
        return (double) overlapDuration / counterState.getSubWindowSizeMillis();
    }
    
    //현재 서브 윈도우 시작 시간 계산
    private long getCurrentSubWindowStart(SlidingWindowCounterState counterState, long currentTime) {
        return (currentTime / counterState.getSubWindowSizeMillis()) * counterState.getSubWindowSizeMillis();
    }
    
    //다음 요청이 가능한 시간 계산
    private long calculateResetTime(SlidingWindowCounterState counterState, long currentTime) {
        // 가장 오래된 서브 윈도우가 만료되는 시간
        long oldestSubWindow = counterState.getWindowCounts().keySet().stream()
                .min(Long::compareTo)
                .orElse(currentTime);
        
        return oldestSubWindow + counterState.getWindowSizeMillis();
    }
    
    @Override
    public void reset(Long userId) {
        counters.remove(userId);
        log.debug("Reset counter for user: {}", userId);
    }
    
    @Override
    public Map<String, Object> getStats(Long userId) {
        SlidingWindowCounterState counterState = counters.get(userId);
        Map<String, Object> stats = new HashMap<>();
        
        if (counterState != null) {
            synchronized (counterState) {
                // 현재 시간 기준으로 카운터 정리
                cleanupExpiredSubWindows(counterState, TimeUtil.currentTimeMillis());
                
                double currentRequestCount = calculateWeightedRequestCount(counterState, TimeUtil.currentTimeMillis());
                
                stats.put("algorithm", getAlgorithmName());
                stats.put("userId", userId);
                stats.put("currentWeightedRequests", currentRequestCount);
                stats.put("limit", counterState.getLimit());
                stats.put("remainingRequests", Math.max(0, defaultLimit - (long) currentRequestCount));
                stats.put("windowSizeSeconds", counterState.getWindowSizeMillis() / 1000);
                stats.put("subWindowSizeSeconds", counterState.getSubWindowSizeMillis() / 1000);
                stats.put("subWindowCount", subWindowCount);
                stats.put("activeSubWindows", counterState.getWindowCounts().size());
                
                // 서브 윈도우 세부 정보 (디버깅용)
                Map<String, Object> subWindowDetails = new HashMap<>();
                for (Map.Entry<Long, Long> entry : counterState.getWindowCounts().entrySet()) {
                    String windowKey = TimeUtil.formatTimestamp(entry.getKey());
                    subWindowDetails.put(windowKey, entry.getValue());
                }
                stats.put("subWindowCounts", subWindowDetails);
            }
        } else {
            stats.put("algorithm", getAlgorithmName());
            stats.put("userId", userId);
            stats.put("status", "No counter found");
        }
        
        return stats;
    }
    
    @Override
    public String getAlgorithmName() {
        return "sliding-window-counter";
    }
    
    //모든 카운터 정리 (테스트 또는 메모리 관리 용도)
    public void clearAllCounters() {
        counters.clear();
        log.debug("All counters cleared");
    }
    
    //비활성 카운터 정리 (메모리 관리 용도)
    public int cleanupInactiveCounters(long inactiveThresholdMillis) {
        long currentTime = TimeUtil.currentTimeMillis();
        final int[] removedCount = {0};
        
        counters.entrySet().removeIf(entry -> {
            SlidingWindowCounterState counterState = entry.getValue();
            synchronized (counterState) {
                cleanupExpiredSubWindows(counterState, currentTime);
                
                // 활성 서브 윈도우가 없거나 모든 서브 윈도우가 임계값보다 오래된 경우
                boolean isInactive = counterState.getWindowCounts().isEmpty();
                if (!isInactive) {
                    long newestSubWindow = counterState.getWindowCounts().keySet().stream()
                            .max(Long::compareTo)
                            .orElse(0L);
                    isInactive = (currentTime - newestSubWindow) > inactiveThresholdMillis;
                }
                
                if (isInactive) {
                    removedCount[0]++;
                }
                return isInactive;
            }
        });
        
        if (removedCount[0] > 0) {
            log.info("Cleaned up {} inactive counters", removedCount[0]);
        }
        
        return removedCount[0];
    }
}
