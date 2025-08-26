package com.example.demo.ratelimiter.algo.bucket;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Algorithm
 * 
 * 동작 원리:
 * - 고정된 용량의 버킷에 일정한 속도로 토큰을 추가
 * - 요청 시 토큰을 소비하며, 토큰이 없으면 요청 거부
 * - 버스트 트래픽 처리에 유리
 * 
 * 장점:
 * - 버스트 트래픽을 허용하면서도 장기적으로 평균 속도를 제한
 * - 구현이 비교적 간단
 * - 메모리 효율적
 * 
 * 단점:
 * - 순간적으로 많은 요청이 몰릴 수 있음
 * - 정확한 시간 기반 제어가 어려움
 */
@Component
public class TokenBucketLimiter implements RateLimiter {
    
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitConfig defaultConfig;
    
    public TokenBucketLimiter() {
        this.defaultConfig = RateLimitConfig.forTokenBucket(10, 1); // 10개 토큰, 초당 1개 보충
    }
    
    public TokenBucketLimiter(RateLimitConfig config) {
        this.defaultConfig = config;
    }
    
    @Override
    public RateLimitResult tryAcquire(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(defaultConfig));
        return bucket.tryConsume();
    }
    
    @Override
    public RateLimitResult getStatus(String key) {
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            return RateLimitResult.allowed(defaultConfig.getCapacity(), 0, "TOKEN_BUCKET");
        }
        return bucket.getStatus();
    }
    
    @Override
    public void reset(String key) {
        buckets.remove(key);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(config));
        return bucket.tryConsume();
    }
    
    /**
     * Token Bucket 내부 구현 클래스
     */
    private static class TokenBucket {
        private final AtomicLong tokens;
        private final AtomicLong lastRefillTime;
        private final RateLimitConfig config;
        
        public TokenBucket(RateLimitConfig config) {
            this.config = config;
            this.tokens = new AtomicLong(config.getCapacity());
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }
        
        public RateLimitResult tryConsume() {
            refill();
            
            long currentTokens = tokens.get();
            if (currentTokens > 0) {
                // CAS (Compare-And-Swap)를 사용한 thread-safe 토큰 소비
                if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                    return RateLimitResult.allowed(
                        currentTokens - 1, 
                        getNextRefillTime(), 
                        "TOKEN_BUCKET",
                        "Token consumed successfully"
                    );
                }
                // CAS 실패 시 재시도
                return tryConsume();
            }
            
            return RateLimitResult.denied(
                0, 
                getNextRefillTime(), 
                "TOKEN_BUCKET",
                "No tokens available"
            );
        }
        
        public RateLimitResult getStatus() {
            refill();
            return RateLimitResult.allowed(
                tokens.get(), 
                getNextRefillTime(), 
                "TOKEN_BUCKET",
                "Current status"
            );
        }
        
        /**
         * 토큰 보충 로직
         */
        private void refill() {
            long now = System.currentTimeMillis();
            long lastRefill = lastRefillTime.get();
            
            if (now > lastRefill) {
                long elapsed = now - lastRefill;
                long tokensToAdd = (elapsed / 1000) * config.getRefillRate();
                
                if (tokensToAdd > 0) {
                    long currentTokens = tokens.get();
                    long newTokens = Math.min(config.getCapacity(), currentTokens + tokensToAdd);
                    
                    // CAS를 사용한 thread-safe 토큰 보충
                    if (tokens.compareAndSet(currentTokens, newTokens)) {
                        lastRefillTime.set(now);
                    }
                }
            }
        }
        
        /**
         * 다음 토큰 보충 시간 계산
         */
        private long getNextRefillTime() {
            return lastRefillTime.get() + 1000; // 1초 후
        }
    }
} 