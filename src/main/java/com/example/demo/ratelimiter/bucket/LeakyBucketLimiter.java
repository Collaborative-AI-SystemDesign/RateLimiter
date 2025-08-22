package com.example.demo.ratelimiter.bucket;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Leaky Bucket Algorithm
 * 
 * 동작 원리:
 * - 고정된 용량의 버킷에서 일정한 속도로 요청을 처리 (leak)
 * - 버킷이 가득 차면 새로운 요청 거부
 * - 일정한 출력 속도 보장 (트래픽 평활화)
 * 
 * 장점:
 * - 일정한 출력 속도를 보장하여 트래픽을 평활화
 * - 네트워크 대역폭 제어에 효과적
 * - 백엔드 시스템 보호에 유리
 * 
 * 단점:
 * - 버스트 트래픽을 처리할 수 없음
 * - 유연성이 떨어짐
 * - 메모리 사용량이 상대적으로 많음
 */
@Component
public class LeakyBucketLimiter implements RateLimiter {
    
    private final ConcurrentHashMap<String, LeakyBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitConfig defaultConfig;
    
    public LeakyBucketLimiter() {
        this.defaultConfig = RateLimitConfig.forTokenBucket(10, 1); // 10개 용량, 초당 1개 처리
    }
    
    public LeakyBucketLimiter(RateLimitConfig config) {
        this.defaultConfig = config;
    }
    
    @Override
    public RateLimitResult tryAcquire(String key) {
        LeakyBucket bucket = buckets.computeIfAbsent(key, k -> new LeakyBucket(defaultConfig));
        return bucket.tryAdd();
    }
    
    @Override
    public RateLimitResult getStatus(String key) {
        LeakyBucket bucket = buckets.get(key);
        if (bucket == null) {
            return RateLimitResult.allowed(defaultConfig.getCapacity(), 0, "LEAKY_BUCKET");
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
        LeakyBucket bucket = buckets.computeIfAbsent(key, k -> new LeakyBucket(config));
        return bucket.tryAdd();
    }
    
    /**
     * Leaky Bucket 내부 구현 클래스
     */
    private static class LeakyBucket {
        private final LinkedBlockingQueue<Long> requests;
        private final AtomicLong lastLeakTime;
        private final RateLimitConfig config;
        
        public LeakyBucket(RateLimitConfig config) {
            this.config = config;
            this.requests = new LinkedBlockingQueue<>(config.getCapacity());
            this.lastLeakTime = new AtomicLong(System.currentTimeMillis());
        }
        
        public RateLimitResult tryAdd() {
            leak();
            
            long now = System.currentTimeMillis();
            if (requests.offer(now)) {
                return RateLimitResult.allowed(
                    config.getCapacity() - requests.size(),
                    getNextLeakTime(),
                    "LEAKY_BUCKET",
                    "Request added to bucket"
                );
            }
            
            return RateLimitResult.denied(
                0,
                getNextLeakTime(),
                "LEAKY_BUCKET",
                "Bucket is full"
            );
        }
        
        public RateLimitResult getStatus() {
            leak();
            return RateLimitResult.allowed(
                config.getCapacity() - requests.size(),
                getNextLeakTime(),
                "LEAKY_BUCKET",
                "Current bucket status"
            );
        }
        
        /**
         * 요청 누출(처리) 로직
         * 일정한 속도로 요청을 제거
         */
        private void leak() {
            long now = System.currentTimeMillis();
            long lastLeak = lastLeakTime.get();
            
            if (now > lastLeak) {
                long elapsed = now - lastLeak;
                // 경과 시간에 따라 처리할 수 있는 요청 수 계산
                long requestsToLeak = (elapsed / 1000) * config.getRefillRate();
                
                // 계산된 수만큼 요청을 버킷에서 제거 (처리됨을 의미)
                for (int i = 0; i < requestsToLeak && !requests.isEmpty(); i++) {
                    requests.poll();
                }
                
                if (requestsToLeak > 0) {
                    lastLeakTime.set(now);
                }
            }
        }
        
        /**
         * 다음 누출 시간 계산
         */
        private long getNextLeakTime() {
            return lastLeakTime.get() + 1000; // 1초 후
        }
    }
} 