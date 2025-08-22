package com.example.demo.ratelimiter.core;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Rate Limiting 알고리즘의 공통 인터페이스
 */
public interface RateLimiter {
    
    /**
     * 요청 허용 여부를 검사합니다.
     * 
     * @param userId 유저 식별자
     * @param request HTTP 요청 객체
     * @return Rate Limiting 결과
     */
    RateLimitResult allowRequest(Long userId, HttpServletRequest request);
    
    /**
     * 특정 클라이언트의 Rate Limiting 상태를 초기화합니다.
     * 주로 테스트 용도로 사용됩니다.
     * 
     * @param userId 유저 식별자
     */
    void reset(Long userId);
    
    /**
     * 특정 클라이언트의 현재 상태 정보를 반환합니다.
     * 
     * @param userId 유저 식별자
     * @return 상태 정보 Map
     */
    Map<String, Object> getStats(Long userId);
    
    /**
     * 알고리즘 이름을 반환합니다.
     * 
     * @return 알고리즘 이름
     */
    String getAlgorithmName();
}
