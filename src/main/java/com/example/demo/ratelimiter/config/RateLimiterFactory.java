package com.example.demo.ratelimiter.config;

import com.example.demo.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.example.demo.ratelimiter.algorithm.LeakyBucketRateLimiter;
import com.example.demo.ratelimiter.algorithm.SlidingWindowCounterRateLimiter;
import com.example.demo.ratelimiter.algorithm.SlidingWindowLogRateLimiter;
import com.example.demo.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.example.demo.ratelimiter.core.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter 인스턴스를 생성하고 관리하는 팩토리 클래스
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterFactory {
    
    private final RateLimiterProperties properties;
    private final ConcurrentHashMap<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();
    
    //기본 알고리즘의 Rate Limiter 인스턴스 반환
    public RateLimiter getRateLimiter() {
        return getRateLimiter(properties.getDefaultAlgorithm());
    }
    
    //지정된 알고리즘의 Rate Limiter 인스턴스 반환
    public RateLimiter getRateLimiter(String algorithmName) {
        return rateLimiterCache.computeIfAbsent(algorithmName, this::createRateLimiter);
    }
    
    //URL 패턴에 맞는 Rate Limiter 인스턴스 반환
    public RateLimiter getRateLimiterForPattern(String urlPattern) {
        RateLimiterProperties.UrlPatternConfig patternConfig = properties.getUrlPatterns().get(urlPattern);
        
        if (patternConfig != null && patternConfig.getAlgorithm() != null) {
            String cacheKey = urlPattern + ":" + patternConfig.getAlgorithm();
            return rateLimiterCache.computeIfAbsent(cacheKey, 
                    k -> createRateLimiterForPattern(patternConfig));
        }
        
        return getRateLimiter();
    }
    
    //알고리즘 이름에 따른 Rate Limiter 인스턴스 생성
    private RateLimiter createRateLimiter(String algorithmName) {
        return switch (algorithmName.toLowerCase()) {
            case "token-bucket" -> createTokenBucketRateLimiter();
            case "leaky-bucket" -> createLeakyBucketRateLimiter();
            case "fixed-window" -> createFixedWindowRateLimiter();
            case "sliding-window-log" -> createSlidingWindowLogRateLimiter();
            case "sliding-window-counter" -> createSlidingWindowCounterRateLimiter();
            default -> {
                log.warn("Unknown algorithm: {}, using default token bucket", algorithmName);
                yield createTokenBucketRateLimiter();
            }
        };
    }
    
    //Token Bucket Rate Limiter 생성
    private RateLimiter createTokenBucketRateLimiter() {
        RateLimiterProperties.AlgorithmConfig config = properties.getTokenBucketConfig();
        
        int capacity = config.getCapacity() != null ? config.getCapacity() : 100;
        double refillRate = config.getRefillRate() != null ? config.getRefillRate().doubleValue() : 10.0;
        
        log.info("Creating TokenBucketRateLimiter with capacity: {}, refillRate: {}", 
                capacity, refillRate);
        
        return new TokenBucketRateLimiter(capacity, refillRate);
    }

    //Leaky Bucket Rate Limiter 생성
    private RateLimiter createLeakyBucketRateLimiter() {
        RateLimiterProperties.AlgorithmConfig config = properties.getLeakyBucketConfig();

        int capacity = config.getBucketSize() != null ? config.getBucketSize() : 100;
        double leakRate = config.getLeakRate() != null ? config.getLeakRate().doubleValue() : 10.0;

        log.info("Creating LeakyBucketRateLimiter with capacity: {}, leakRate: {}", capacity, leakRate);

        return new LeakyBucketRateLimiter(capacity, leakRate);
    }

    //Fixed Window Rate Limiter 생성
    private RateLimiter createFixedWindowRateLimiter() {
        RateLimiterProperties.AlgorithmConfig config = properties.getFixedWindowConfig();

        int limit = config.getLimit() != null ? config.getLimit() : 100;
        long windowSize = config.getWindowSize() != null ? config.getWindowSize().longValue() : 60;

        log.info("Creating FixedWindowRateLimiter with limit: {}, windowSize: {}s", limit, windowSize);

        return new FixedWindowRateLimiter(limit, windowSize);
    }

    //Sliding Window Log Rate Limiter 생성
    private RateLimiter createSlidingWindowLogRateLimiter() {
        RateLimiterProperties.AlgorithmConfig config = properties.getSlidingWindowLogConfig();

        int limit = config.getLimit() != null ? config.getLimit() : 100;
        long windowSize = config.getWindowSize() != null ? config.getWindowSize().longValue() : 60;

        log.info("Creating SlidingWindowLogRateLimiter with limit: {}, windowSize: {}s", limit, windowSize);

        return new SlidingWindowLogRateLimiter(limit, windowSize);
    }

    //Sliding Window Counter Rate Limiter 생성
    private RateLimiter createSlidingWindowCounterRateLimiter() {
        RateLimiterProperties.AlgorithmConfig config = properties.getSlidingWindowCounterConfig();

        int limit = config.getLimit() != null ? config.getLimit() : 100;
        long windowSize = config.getWindowSize() != null ? config.getWindowSize().longValue() : 60;
        int subWindowCount = 6; // 기본적으로 6개의 서브 윈도우

        log.info("Creating SlidingWindowCounterRateLimiter with limit: {}, windowSize: {}s, subWindows: {}", 
                limit, windowSize, subWindowCount);

        return new SlidingWindowCounterRateLimiter(limit, windowSize, subWindowCount);
    }
    
    //URL 패턴 설정에 따른 Rate Limiter 생성
    private RateLimiter createRateLimiterForPattern(RateLimiterProperties.UrlPatternConfig patternConfig) {
        String algorithm = patternConfig.getAlgorithm().toLowerCase();
        
        switch (algorithm) {
            case "token-bucket":
                int capacity = patternConfig.getCapacity() != null ? 
                        patternConfig.getCapacity() : 100;
                double refillRate = patternConfig.getRefillRate() != null ? 
                        patternConfig.getRefillRate().doubleValue() : 10.0;
                
                log.info("Creating TokenBucketRateLimiter for pattern with capacity: {}, refillRate: {}", 
                        capacity, refillRate);
                return new TokenBucketRateLimiter(capacity, refillRate);

            case "leaky-bucket":
                int leakyCapacity = patternConfig.getCapacity() != null ? 
                        patternConfig.getCapacity() : 100;
                double leakRate = patternConfig.getRefillRate() != null ? 
                        patternConfig.getRefillRate().doubleValue() : 10.0;
                
                log.info("Creating LeakyBucketRateLimiter for pattern with capacity: {}, leakRate: {}", 
                        leakyCapacity, leakRate);
                return new LeakyBucketRateLimiter(leakyCapacity, leakRate);

            case "fixed-window":
                int fixedLimit = patternConfig.getLimit() != null ? 
                        patternConfig.getLimit() : 100;
                long fixedWindowSize = patternConfig.getWindowSize() != null ? 
                        patternConfig.getWindowSize().longValue() : 60;
                
                log.info("Creating FixedWindowRateLimiter for pattern with limit: {}, windowSize: {}s", 
                        fixedLimit, fixedWindowSize);
                return new FixedWindowRateLimiter(fixedLimit, fixedWindowSize);

            case "sliding-window-log":
                int logLimit = patternConfig.getLimit() != null ? 
                        patternConfig.getLimit() : 100;
                long logWindowSize = patternConfig.getWindowSize() != null ? 
                        patternConfig.getWindowSize().longValue() : 60;
                
                log.info("Creating SlidingWindowLogRateLimiter for pattern with limit: {}, windowSize: {}s", 
                        logLimit, logWindowSize);
                return new SlidingWindowLogRateLimiter(logLimit, logWindowSize);

            case "sliding-window-counter":
                int counterLimit = patternConfig.getLimit() != null ? 
                        patternConfig.getLimit() : 100;
                long counterWindowSize = patternConfig.getWindowSize() != null ? 
                        patternConfig.getWindowSize().longValue() : 60;
                int subWindowCount = 6; // 기본적으로 6개의 서브 윈도우
                
                log.info("Creating SlidingWindowCounterRateLimiter for pattern with limit: {}, windowSize: {}s, subWindows: {}", 
                        counterLimit, counterWindowSize, subWindowCount);
                return new SlidingWindowCounterRateLimiter(counterLimit, counterWindowSize, subWindowCount);

            default:
                log.warn("Unknown algorithm in pattern config: {}, using default", algorithm);
                return createTokenBucketRateLimiter();
        }
    }
}
