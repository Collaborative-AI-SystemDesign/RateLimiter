package com.example.demo.ratelimiter.util;

import com.example.demo.ratelimiter.core.RateLimitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiting 응답 처리 유틸리티
 */
@Slf4j
public class ResponseUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Rate Limiting 관련 HTTP 헤더
    private static final String HEADER_RATE_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String HEADER_RATE_LIMIT_ALGORITHM = "X-RateLimit-Algorithm";
    
    /**
     * Rate Limiting 응답 헤더 설정
     */
    public static void setRateLimitHeaders(HttpServletResponse response, RateLimitResult result, int limit) {
        // 기본 Rate Limiting 헤더
        response.setHeader(HEADER_RATE_LIMIT, String.valueOf(limit));
        response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(result.getRemainingRequests()));
        response.setHeader(HEADER_RATE_LIMIT_RESET, String.valueOf(result.getResetTimeMillis() / 1000));
        response.setHeader(HEADER_RATE_LIMIT_ALGORITHM, result.getAlgorithm());
        
        // 요청이 거부된 경우 Retry-After 헤더 추가
        if (!result.isAllowed() && result.getRetryAfterSeconds() > 0) {
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.getRetryAfterSeconds()));
        }
        
        log.debug("Rate limit headers set - limit: {}, remaining: {}, reset: {}, algorithm: {}", 
                limit, result.getRemainingRequests(), result.getResetTimeMillis() / 1000, result.getAlgorithm());
    }
    
    /**
     * 429 Too Many Requests 응답 생성
     */
    public static void sendTooManyRequestsResponse(HttpServletResponse response, RateLimitResult result) 
            throws IOException {
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // JSON 응답 본문 생성
        Map<String, Object> errorResponse = createErrorResponse(result);
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
        
        log.debug("429 response sent for algorithm: {}, retry after: {}s", 
                result.getAlgorithm(), result.getRetryAfterSeconds());
    }
    
    /**
     * 에러 응답 JSON 객체 생성
     */
    private static Map<String, Object> createErrorResponse(RateLimitResult result) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Too Many Requests");
        error.put("message", "Rate limit exceeded. Please try again later.");
        error.put("status", 429);
        error.put("timestamp", System.currentTimeMillis());
        
        // Rate Limiting 상세 정보
        Map<String, Object> rateLimitInfo = new HashMap<>();
        rateLimitInfo.put("algorithm", result.getAlgorithm());
        rateLimitInfo.put("resetTime", result.getResetTimeMillis());
        rateLimitInfo.put("retryAfter", result.getRetryAfterSeconds());
        rateLimitInfo.put("resetTimeFormatted", TimeUtil.formatTimestamp(result.getResetTimeMillis()));
        
        error.put("rateLimit", rateLimitInfo);
        
        return error;
    }
    
    /**
     * Rate Limiting 성공 응답 로깅
     */
    public static void logAllowedRequest(Long userId, RateLimitResult result, String requestPath) {
        log.debug("Request allowed - user: {}, path: {}, remaining: {}, algorithm: {}", 
                userId, requestPath, result.getRemainingRequests(), result.getAlgorithm());
    }
    
    /**
     * Rate Limiting 거부 응답 로깅
     */
    public static void logRejectedRequest(Long userId, RateLimitResult result, String requestPath) {
        log.warn("Request rejected - user: {}, path: {}, algorithm: {}, retry after: {}s", 
                userId, requestPath, result.getAlgorithm(), result.getRetryAfterSeconds());
    }
}
