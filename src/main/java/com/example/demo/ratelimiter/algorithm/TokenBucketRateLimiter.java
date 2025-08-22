package com.example.demo.ratelimiter.algorithm;

import com.example.demo.ratelimiter.core.RateLimiter;
import com.example.demo.ratelimiter.core.RateLimitResult;
import com.example.demo.ratelimiter.model.TokenBucketState;
import com.example.demo.ratelimiter.util.TimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TokenBucketRateLimiter implements RateLimiter {
    
    private final ConcurrentHashMap<Long, TokenBucketState> buckets = new ConcurrentHashMap<>();
    private final int defaultCapacity;
    private final double defaultRefillRate; // tokens per second

    public TokenBucketRateLimiter() {
        this(100, 10.0); // 기본: 100개 토큰, 초당 10개 보충
    }
    
    /**
     * 사용자 정의 설정으로 TokenBucketRateLimiter 생성
     * 
     * @param capacity 버킷 용량 (최대 토큰 수)
     * @param refillRate 토큰 보충 속도 (초당 토큰 수)
     */
    public TokenBucketRateLimiter(int capacity, double refillRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (refillRate <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive");
        }
        
        this.defaultCapacity = capacity;
        this.defaultRefillRate = refillRate;
        
        log.info("TokenBucketRateLimiter initialized - capacity: {}, refillRate: {}", 
                capacity, refillRate);
    }
    
    @Override
    public RateLimitResult allowRequest(Long userId, HttpServletRequest request) {
        long currentTime = TimeUtil.currentTimeMillis();
        
        // 사용자별 버킷 상태 가져오기 또는 생성
        TokenBucketState bucket = buckets.computeIfAbsent(userId,
                k -> TokenBucketState.createTokenBucket(defaultCapacity, defaultRefillRate));
        
        // 동기화된 블록에서 토큰 업데이트 및 소모 처리
        synchronized (bucket) {
            // 토큰 보충
            refillTokens(bucket, currentTime);
            
            // 토큰이 있는지 확인하고 소모
            if (bucket.getTokens() >= 1.0) {
                bucket.setTokens(bucket.getTokens() - 1.0);
                
                long remainingRequests = (long) Math.floor(bucket.getTokens());
                long resetTime = calculateResetTime(bucket, currentTime);
                
                log.debug("Request allowed for user: {} - remaining tokens: {}", 
                        userId, bucket.getTokens());
                
                return RateLimitResult.allowed(remainingRequests, resetTime, getAlgorithmName());
            } else {
                // 토큰이 부족한 경우
                long resetTime = calculateResetTime(bucket, currentTime);
                long retryAfter = TimeUtil.calculateRetryAfterSeconds(resetTime);
                
                log.debug("Request rejected for user: {} - tokens: {}, retry after: {}s", 
                        userId, bucket.getTokens(), retryAfter);
                
                return RateLimitResult.rejected(resetTime, getAlgorithmName(), retryAfter);
            }
        }
    }
    
    //토큰 보충 로직
    private void refillTokens(TokenBucketState bucket, long currentTime) {
        double elapsedSeconds = TimeUtil.calculateElapsedSeconds(bucket.getLastUpdateTime(), currentTime);
        
        if (elapsedSeconds > 0) {
            // 경과 시간에 따른 새로운 토큰 계산
            double newTokens = elapsedSeconds * bucket.getRefillRate();
            double updatedTokens = Math.min(bucket.getCapacity(), bucket.getTokens() + newTokens);
            
            bucket.setTokens(updatedTokens);
            bucket.setLastUpdateTime(currentTime);
            
            log.trace("Tokens refilled - client tokens: {}, elapsed: {}s, new tokens: {}", 
                    updatedTokens, elapsedSeconds, newTokens);
        }
    }
    
    //다음 토큰이 보충될 시간 계산
    private long calculateResetTime(TokenBucketState bucket, long currentTime) {
        if (bucket.getTokens() >= bucket.getCapacity()) {
            return currentTime; // 이미 가득 참
        }
        
        // 다음 토큰이 추가될 때까지의 시간 계산
        double timeToNextToken = 1.0 / bucket.getRefillRate(); // 다음 토큰까지 초
        return currentTime + (long) (timeToNextToken * 1000); // 밀리초로 변환
    }
    
    @Override
    public void reset(Long userId) {
        buckets.remove(userId);
        log.debug("Reset bucket for user: {}", userId);
    }
    
    @Override
    public Map<String, Object> getStats(Long userId) {
        TokenBucketState bucket = buckets.get(userId);
        Map<String, Object> stats = new HashMap<>();
        
        if (bucket != null) {
            synchronized (bucket) {
                // 현재 시간 기준으로 토큰 업데이트
                refillTokens(bucket, TimeUtil.currentTimeMillis());
                
                stats.put("algorithm", getAlgorithmName());
                stats.put("userId", userId);
                stats.put("currentTokens", bucket.getTokens());
                stats.put("capacity", bucket.getCapacity());
                stats.put("refillRate", bucket.getRefillRate());
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
        return "token-bucket";
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
            TokenBucketState bucket = entry.getValue();
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
