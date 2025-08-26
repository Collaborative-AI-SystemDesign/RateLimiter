package com.example.demo.ratelimiter.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Rate Limit 처리 결과를 담는 클래스
 */
@Getter
@Builder
@AllArgsConstructor
public class RateLimitResult {
    
    private final boolean allowed;        // 요청 허용 여부
    private final long remainingTokens;   // 남은 토큰/요청 수
    private final long resetTime;         // 다음 리셋 시간 (milliseconds)
    private final String algorithm;       // 사용된 알고리즘
    private final String message;         // 상태 메시지
    
    /**
     * 허용된 요청에 대한 결과 생성
     */
    public static RateLimitResult allowed(long remainingTokens, long resetTime, String algorithm) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(remainingTokens)
                .resetTime(resetTime)
                .algorithm(algorithm)
                .message("Request allowed")
                .build();
    }
    
    /**
     * 거부된 요청에 대한 결과 생성
     */
    public static RateLimitResult denied(long remainingTokens, long resetTime, String algorithm) {
        return RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(remainingTokens)
                .resetTime(resetTime)
                .algorithm(algorithm)
                .message("Rate limit exceeded")
                .build();
    }
    
    /**
     * 커스텀 메시지와 함께 허용된 요청 결과 생성
     */
    public static RateLimitResult allowed(long remainingTokens, long resetTime, String algorithm, String message) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(remainingTokens)
                .resetTime(resetTime)
                .algorithm(algorithm)
                .message(message)
                .build();
    }
    
    /**
     * 커스텀 메시지와 함께 거부된 요청 결과 생성
     */
    public static RateLimitResult denied(long remainingTokens, long resetTime, String algorithm, String message) {
        return RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(remainingTokens)
                .resetTime(resetTime)
                .algorithm(algorithm)
                .message(message)
                .build();
    }
} 