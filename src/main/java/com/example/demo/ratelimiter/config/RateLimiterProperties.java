package com.example.demo.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiter 설정 프로퍼티
 */
@Data
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private boolean enabled = true; // Rate Limiter 활성화
    private String defaultAlgorithm = "token-bucket"; // 기본 알고리즘
    private Map<String, AlgorithmConfig> algorithms = new HashMap<>(); // 알고리즘 설정 맵
    private Map<String, UrlPatternConfig> urlPatterns = new HashMap<>(); // URL 패턴별 Rate Limiter 설정
    
    @Data
    public static class AlgorithmConfig {
        // Token Bucket 설정
        private Integer capacity;
        private Integer refillRate;
        
        // Fixed Window 설정
        private Integer limit;
        private Integer windowSize;
        
        // Leaky Bucket 설정
        private Integer bucketSize;
        private Integer leakRate;
        
        // Sliding Window 설정
        private Integer windowSizeSeconds;
        private Integer maxRequests;
    }
    
    @Data
    public static class UrlPatternConfig {
        private String algorithm;
        private Integer limit;
        private Integer capacity;
        private Integer refillRate;
        private Integer windowSize;
    }
    
    /**
     * 기본 Token Bucket 설정 반환
     */
    public AlgorithmConfig getTokenBucketConfig() {
        AlgorithmConfig config = algorithms.get("token-bucket");
        if (config == null) {
            config = new AlgorithmConfig();
            config.setCapacity(100);
            config.setRefillRate(10);
        }
        return config;
    }

    public AlgorithmConfig getLeakyBucketConfig() {
        AlgorithmConfig config = algorithms.get("leaky-bucket");
        if (config == null) {
            config = new AlgorithmConfig();
            config.setBucketSize(100);
            config.setLeakRate(10);
        }
        return config;
    }
    
    /**
     * 기본 Fixed Window 설정 반환
     */
    public AlgorithmConfig getFixedWindowConfig() {
        AlgorithmConfig config = algorithms.get("fixed-window");
        if (config == null) {
            config = new AlgorithmConfig();
            config.setLimit(100);
            config.setWindowSize(60);
        }
        return config;
    }
    
    /**
     * 기본 Sliding Window Log 설정 반환
     */
    public AlgorithmConfig getSlidingWindowLogConfig() {
        AlgorithmConfig config = algorithms.get("sliding-window-log");
        if (config == null) {
            config = new AlgorithmConfig();
            config.setLimit(100);
            config.setWindowSize(60);
        }
        return config;
    }
    
    /**
     * 기본 Sliding Window Counter 설정 반환
     */
    public AlgorithmConfig getSlidingWindowCounterConfig() {
        AlgorithmConfig config = algorithms.get("sliding-window-counter");
        if (config == null) {
            config = new AlgorithmConfig();
            config.setLimit(100);
            config.setWindowSize(60);
        }
        return config;
    }
}
