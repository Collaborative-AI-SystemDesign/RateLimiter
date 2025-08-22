package com.example.demo.ratelimiter.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Rate Limiting 결과를 담는 클래스
 */
@Getter
@Builder
@AllArgsConstructor
public class RateLimitResult {

    private final boolean allowed; // 요청 허용 여부 (true: 허용, false: 거부)
    private final long remainingRequests; // 남은 요청 수
    private final long resetTimeMillis; // 리셋 시간 (epoch 밀리초 단위)
    private final String algorithm; // 사용된 알고리즘 이름
    private final long retryAfterSeconds; // 재시도 권장 시간 (초)
    
    //허용된 요청을 생성하는 정적 메소드
    public static RateLimitResult allowed(long remainingRequests, long resetTimeMillis, String algorithm) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingRequests(remainingRequests)
                .resetTimeMillis(resetTimeMillis)
                .algorithm(algorithm)
                .retryAfterSeconds(0)
                .build();
    }
    
    //거부된 요청을 생성하는 정적 메소드
    public static RateLimitResult rejected(long resetTimeMillis, String algorithm, long retryAfterSeconds) {
        return RateLimitResult.builder()
                .allowed(false)
                .remainingRequests(0)
                .resetTimeMillis(resetTimeMillis)
                .algorithm(algorithm)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}
