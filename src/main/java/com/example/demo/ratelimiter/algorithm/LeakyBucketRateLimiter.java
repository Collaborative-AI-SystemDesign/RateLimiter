package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimitResult;
import com.example.demo.ratelimiter.core.RateLimiter;
import com.example.demo.ratelimiter.model.LeakyBucketState;
import com.example.demo.ratelimiter.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class LeakyBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<Long, LeakyBucketState> buckets = new ConcurrentHashMap<>();
    private final int defaultCapacity;
    private final double defaultLeakRate; // requests per second

    public LeakyBucketRateLimiter() {
        this(100, 10.0); // 기본: 100개 용량, 초당 10개 누출
    }

    /**
     * 사용자 정의 설정으로 LeakyBucketRateLimiter 생성
     *
     * @param capacity 버킷 용량 (최대 요청 수)
     * @param leakRate 요청 누출 속도 (초당 요청 수)
     */
    public LeakyBucketRateLimiter(int capacity, double leakRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (leakRate <= 0) {
            throw new IllegalArgumentException("Leak rate must be positive");
        }
        
        this.defaultCapacity = capacity;
        this.defaultLeakRate = leakRate;
        
        log.info("LeakyBucketRateLimiter initialized - capacity: {}, leakRate: {}",
                capacity, leakRate);
    }
    
    @Override
    public RateLimitResult allowRequest(Long userId, HttpServletRequest request) {
        long currentTime = TimeUtil.currentTimeMillis();
        
        // 사용자별 버킷 상태 가져오기 또는 생성
        LeakyBucketState bucket = buckets.computeIfAbsent(userId,
                k -> LeakyBucketState.createLeakyBucket(defaultCapacity, defaultLeakRate));
        
        // 동기화된 블록에서 요청 처리
        synchronized (bucket) {
            // 요청 누출 (시간에 따른 버킷 용량 회복)
            leakRequests(bucket, currentTime);
            
            // 버킷에 공간이 있는지 확인
            if (bucket.getRequests() < defaultCapacity) {
                // 요청 추가 (버킷에 요청 하나 추가)
                bucket.setRequests(bucket.getRequests() + 1.0);
                
                long remainingRequests = (long) Math.floor(defaultCapacity - bucket.getRequests());
                long resetTime = calculateResetTime(bucket, currentTime);
                
                log.debug("Request allowed for user: {} - current requests in bucket: {}", 
                        userId, bucket.getRequests());
                
                return RateLimitResult.allowed(remainingRequests, resetTime, getAlgorithmName());
            } else {
                // 버킷이 가득 찬 경우 (더 이상 요청을 받을 수 없음)
                long resetTime = calculateResetTime(bucket, currentTime);
                long retryAfter = TimeUtil.calculateRetryAfterSeconds(resetTime);
                
                log.debug("Request rejected for user: {} - bucket full with {} requests, retry after: {}s", 
                        userId, bucket.getRequests(), retryAfter);
                
                return RateLimitResult.rejected(resetTime, getAlgorithmName(), retryAfter);
            }
        }
    }
    
    //요청 누출 로직 (시간에 따라 버킷에서 요청을 제거)
    private void leakRequests(LeakyBucketState bucket, long currentTime) {
        double elapsedSeconds = TimeUtil.calculateElapsedSeconds(bucket.getLastUpdateTime(), currentTime);
        
        if (elapsedSeconds > 0) {
            // 경과 시간에 따른 누출된 요청 수 계산
            double leakedRequests = elapsedSeconds * bucket.getLeakRate();
            double updatedRequests = Math.max(0, bucket.getRequests() - leakedRequests);
            
            bucket.setRequests(updatedRequests);
            bucket.setLastUpdateTime(currentTime);
            
            log.trace("Requests leaked - remaining requests: {}, elapsed: {}s, leaked: {}", 
                    updatedRequests, elapsedSeconds, leakedRequests);
        }
    }
    
    //다음 요청이 처리 가능한 시간 계산
    private long calculateResetTime(LeakyBucketState bucket, long currentTime) {
        if (bucket.getRequests() < defaultCapacity) {
            return currentTime; // 이미 공간이 있음
        }
        
        // 하나의 요청이 누출될 때까지의 시간 계산
        double timeToNextLeak = 1.0 / bucket.getLeakRate(); // 다음 누출까지 초
        return currentTime + (long) (timeToNextLeak * 1000); // 밀리초로 변환
    }
    
    @Override
    public void reset(Long userId) {
        buckets.remove(userId);
        log.debug("Reset bucket for user: {}", userId);
    }
    
    @Override
    public Map<String, Object> getStats(Long userId) {
        LeakyBucketState bucket = buckets.get(userId);
        Map<String, Object> stats = new HashMap<>();
        
        if (bucket != null) {
            synchronized (bucket) {
                // 현재 시간 기준으로 요청 누출 업데이트
                leakRequests(bucket, TimeUtil.currentTimeMillis());
                
                stats.put("algorithm", getAlgorithmName());
                stats.put("userId", userId);
                stats.put("currentRequests", bucket.getRequests());
                stats.put("capacity", bucket.getLimit());
                stats.put("leakRate", bucket.getLeakRate());
                stats.put("availableSpace", defaultCapacity - bucket.getRequests());
                stats.put("lastUpdateTime", bucket.getLastUpdateTime());
                stats.put("lastUpdateTimeFormatted", TimeUtil.formatTimestamp(bucket.getLastUpdateTime()));
            }
        } else {
            stats.put("algorithm", getAlgorithmName());
            stats.put("userId", userId);
            stats.put("status", "No bucket found");
        }
        
        return stats;
    }
    
    @Override
    public String getAlgorithmName() {
        return "leaky-bucket";
    }
    
    //모든 버킷 정리 (테스트 또는 메모리 관리 용도)
    public void clearAllBuckets() {
        buckets.clear();
        log.debug("All buckets cleared");
    }
    

    // 비활성 클라이언트 정리 (메모리 관리 용도)
    // 지정된 시간(밀리초) 이상 사용되지 않은 버킷 제거
    public int cleanupInactiveBuckets(long inactiveThresholdMillis) {
        long currentTime = TimeUtil.currentTimeMillis();
        final int[] removedCount = {0};
        
        buckets.entrySet().removeIf(entry -> {
            LeakyBucketState bucket = entry.getValue();
            boolean isInactive = (currentTime - bucket.getLastUpdateTime()) > inactiveThresholdMillis;
            if (isInactive) {
                removedCount[0]++;
            }
            return isInactive;
        });
        
        if (removedCount[0] > 0) {
            log.info("Cleaned up {} inactive buckets", removedCount[0]);
        }
        
        return removedCount[0];
    }
}
