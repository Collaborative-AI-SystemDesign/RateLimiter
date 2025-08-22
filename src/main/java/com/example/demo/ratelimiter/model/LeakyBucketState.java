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
public class LeakyBucketState {
    private double requests; // 현재 요청 수
    private long lastUpdateTime; // 마지막 업데이트 시간
    private int limit; // 최대 요청 수
    private double leakRate; // 초당 누출 속도

    public static LeakyBucketState createLeakyBucket(int limit, double leakRate) {
        return new LeakyBucketState(0, System.currentTimeMillis(), limit, leakRate);
    }
}
