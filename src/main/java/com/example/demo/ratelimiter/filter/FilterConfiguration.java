package com.example.demo.ratelimiter.filter;

import com.example.demo.ratelimiter.config.RateLimiterFactory;
import com.example.demo.ratelimiter.config.RateLimiterProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Rate Limit Filter 등록 및 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FilterConfiguration {
    
    private final RateLimiterFactory rateLimiterFactory;
    private final RateLimiterProperties rateLimiterProperties;
    
    //RateLimitFilter를 Spring Boot에 등록
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        
        // Filter 생성 및 설정
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                rateLimiterFactory, rateLimiterProperties);
        registration.setFilter(rateLimitFilter);
        
        // URL 패턴 설정 - 모든 API 요청에 적용
        registration.addUrlPatterns("/api/**", "/v1/**", "/v2/**");
        
        // Filter 이름 설정
        registration.setName("rateLimitFilter");
        
        // Filter 순서 설정 (가능한 한 빨리 실행되도록)
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        
        log.info("RateLimitFilter registered with URL patterns: /api/**, /v1/**, /v2/**");
        
        return registration;
    }
}
