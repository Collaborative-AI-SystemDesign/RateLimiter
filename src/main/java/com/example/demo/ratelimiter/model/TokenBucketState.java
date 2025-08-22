package com.example.demo.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token Bucket과 Leaky Bucket에서 사용되는 버킷 상태 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenBucketState {
    private double tokens; // 현재 토큰 수
    private long lastUpdateTime; // 마지막 업데이트 시간
    private int capacity; // 버킷 용량
    private double refillRate; // 토큰 보충 속도

    public static TokenBucketState createTokenBucket(int capacity, double refillRate) {
        return new TokenBucketState(capacity, System.currentTimeMillis(), capacity, refillRate);
    }
}
