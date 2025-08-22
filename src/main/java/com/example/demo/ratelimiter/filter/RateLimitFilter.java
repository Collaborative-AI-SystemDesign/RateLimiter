package com.example.demo.ratelimiter.filter;

import com.example.demo.ratelimiter.config.RateLimiterFactory;
import com.example.demo.ratelimiter.config.RateLimiterProperties;

import com.example.demo.ratelimiter.core.RateLimiter;
import com.example.demo.ratelimiter.core.RateLimitResult;
import com.example.demo.ratelimiter.util.ResponseUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Rate Limiting을 적용하는 서블릿 필터
 * OncePerRequestFilter를 상속받아 요청당 한 번만 실행되도록 보장
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final RateLimiterFactory rateLimiterFactory;
    private final RateLimiterProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                   @NonNull HttpServletResponse response, 
                                   @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        // Rate Limiter가 비활성화된 경우 통과
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // 1. 사용자 ID 추출 (JWT에서)
            Long userId = extractUserId(request);
            
            // 2. 요청 경로에 따른 Rate Limiter 선택
            RateLimiter rateLimiter = selectRateLimiter(request.getRequestURI());
            
            // 3. Rate Limiting 검사
            RateLimitResult result = rateLimiter.allowRequest(userId, request);
            
            // 4. 응답 헤더 설정
            int limit = getRequestLimit(request.getRequestURI());
            ResponseUtil.setRateLimitHeaders(response, result, limit);
            
            // 5. 결과에 따른 처리
            if (result.isAllowed()) {
                // 요청 허용
                ResponseUtil.logAllowedRequest(userId, result, request.getRequestURI());
                filterChain.doFilter(request, response);
            } else {
                // 요청 거부 - 429 응답
                ResponseUtil.logRejectedRequest(userId, result, request.getRequestURI());
                ResponseUtil.sendTooManyRequestsResponse(response, result);
            }
            
        } catch (Exception e) {
            log.error("Error in RateLimitFilter", e);
            // 에러 발생 시 요청을 통과시킴 (fail-open)
            filterChain.doFilter(request, response);
        }
    }
    
    /**
     * JWT 토큰에서 사용자 ID 추출
     * TODO: 실제 JWT 라이브러리를 사용하여 토큰 파싱 및 userId 추출 구현
     */
    private Long extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // "Bearer " 제거
            try {
                // TODO: JWT 라이브러리로 토큰 파싱하여 실제 userId 추출
                // 예: Jwts.parserBuilder().setSigningKey(key).build()
                //     .parseClaimsJws(token).getBody().getSubject()
                
                // 임시: Bearer 토큰 값을 userId로 직접 사용 (테스트용)
                return Long.valueOf(token);
            } catch (NumberFormatException e) {
                log.warn("Invalid Bearer token format: {}, using default userId", token);
            }
        }
        
        // JWT 토큰이 없거나 파싱 실패 시 기본값
        // TODO: 실제 운영환경에서는 인증 오류 처리 필요
        log.debug("No valid JWT token found, using default userId: 123");
        return 123L;
    }
    
    //요청 경로에 따른 Rate Limiter 선택
    private RateLimiter selectRateLimiter(String requestPath) {
        // URL 패턴 매칭으로 특정 경로에 대한 Rate Limiter 찾기
        for (Map.Entry<String, RateLimiterProperties.UrlPatternConfig> entry : 
                properties.getUrlPatterns().entrySet()) {
            
            String pattern = entry.getKey();
            if (pathMatcher.match(pattern, requestPath)) {
                log.debug("Matched pattern '{}' for path '{}'", pattern, requestPath);
                return rateLimiterFactory.getRateLimiterForPattern(pattern);
            }
        }
        
        // 매칭되는 패턴이 없으면 기본 Rate Limiter 사용
        log.debug("No pattern matched for path '{}', using default rate limiter", requestPath);
        return rateLimiterFactory.getRateLimiter();
    }
    
    //요청 경로에 따른 제한 수 반환
    private int getRequestLimit(String requestPath) {
        // URL 패턴별 제한 수 확인
        for (Map.Entry<String, RateLimiterProperties.UrlPatternConfig> entry : 
                properties.getUrlPatterns().entrySet()) {
            
            String pattern = entry.getKey();
            if (pathMatcher.match(pattern, requestPath)) {
                RateLimiterProperties.UrlPatternConfig config = entry.getValue();
                if (config.getLimit() != null) {
                    return config.getLimit();
                }
                if (config.getCapacity() != null) {
                    return config.getCapacity();
                }
            }
        }
        
        // 기본 제한 수 반환
        RateLimiterProperties.AlgorithmConfig defaultConfig = properties.getTokenBucketConfig();
        return defaultConfig.getCapacity() != null ? defaultConfig.getCapacity() : 100;
    }
    
    //특정 경로는 Rate Limiting에서 제외
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // 건강 체크, 메트릭 등은 제외
        if (path.startsWith("/actuator/") || 
            path.startsWith("/health") || 
            path.startsWith("/metrics")) {
            return true;
        }
        
        return false;
    }
}
