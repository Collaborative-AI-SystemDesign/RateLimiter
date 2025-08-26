package com.example.demo.ratelimiter.algo.fixed;

import com.example.demo.ratelimiter.common.RateLimiter;
import com.example.demo.ratelimiter.common.RateLimitResult;
import com.example.demo.ratelimiter.common.RateLimitConfig;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed Window Counter Algorithm
 * 
 * 동작 원리:
 * - 고정된 시간 윈도우 내에서 요청 수를 카운트
 * - 윈도우가 리셋되면 카운터도 리셋
 * - 각 윈도우는 독립적으로 관리됨
**/
@Component
public class FixedWindowCounterLimiter implements RateLimiter {
    
    private final ConcurrentHashMap<String, FixedWindow> windows = new ConcurrentHashMap<>();
    private final RateLimitConfig defaultConfig;
    
    public FixedWindowCounterLimiter() {
        this.defaultConfig = RateLimitConfig.forWindow(10, 60000);
    }
    
    public FixedWindowCounterLimiter(RateLimitConfig config) {
        this.defaultConfig = config;
    }
    
    @Override
    public RateLimitResult tryAcquire(String key) {
        FixedWindow window = windows.computeIfAbsent(key, k -> new FixedWindow(defaultConfig));
        return window.tryIncrement();
    }
    
    @Override
    public RateLimitResult getStatus(String key) {
        FixedWindow window = windows.get(key);
        if (window == null) {
            return RateLimitResult.allowed(
                defaultConfig.getCapacity(), 
                System.currentTimeMillis() + defaultConfig.getWindowSizeMs(), 
                "FIXED_WINDOW"
            );
        }
        return window.getStatus();
    }
    
    @Override
    public void reset(String key) {
        windows.remove(key);
    }
    
    /**
     * 특정 설정으로 요청 처리
     */
    public RateLimitResult tryAcquire(String key, RateLimitConfig config) {
        FixedWindow window = windows.computeIfAbsent(key, k -> new FixedWindow(config));
        return window.tryIncrement();
    }
    
    /**
     * Fixed Window 내부 구현 클래스
     */
    private static class FixedWindow {
        private final AtomicLong counter;
        private final AtomicLong windowStart;
        private final RateLimitConfig config;
        
        public FixedWindow(RateLimitConfig config) {
            this.config = config;
            this.counter = new AtomicLong(0);
            this.windowStart = new AtomicLong(getCurrentWindow());
        }
        
        public RateLimitResult tryIncrement() {
            long currentWindow = getCurrentWindow();
            long windowStartTime = windowStart.get();
            
            // 새로운 윈도우인지 확인
            if (currentWindow > windowStartTime) {
                // 윈도우 리셋 시도 (thread-safe)
                if (windowStart.compareAndSet(windowStartTime, currentWindow)) {
                    counter.set(0);
                }
            }
            
            long currentCount = counter.get();
            if (currentCount < config.getCapacity()) {
                // CAS를 사용한 thread-safe 카운터 증가
                if (counter.compareAndSet(currentCount, currentCount + 1)) {
                    return RateLimitResult.allowed(
                        config.getCapacity() - currentCount - 1,
                        getWindowEnd(),
                        "FIXED_WINDOW",
                        "Request counted in current window"
                    );
                }
                // CAS 실패 시 재시도
                return tryIncrement();
            }
            
            return RateLimitResult.denied(
                0, 
                getWindowEnd(), 
                "FIXED_WINDOW",
                "Window limit exceeded"
            );
        }
        
        public RateLimitResult getStatus() {
            long currentWindow = getCurrentWindow();
            long windowStartTime = windowStart.get();
            
            // 새로운 윈도우라면 리셋된 상태 반환
            if (currentWindow > windowStartTime) {
                return RateLimitResult.allowed(
                    config.getCapacity(), 
                    getWindowEnd(), 
                    "FIXED_WINDOW",
                    "New window - full capacity available"
                );
            }
            
            return RateLimitResult.allowed(
                config.getCapacity() - counter.get(),
                getWindowEnd(),
                "FIXED_WINDOW",
                "Current window status"
            );
        }
        
        /**
         * 현재 윈도우 번호 계산
         * 시간을 윈도우 크기로 나누어 윈도우 식별
         */
        private long getCurrentWindow() {
            return System.currentTimeMillis() / config.getWindowSizeMs();
        }
        
        /**
         * 현재 윈도우 종료 시간 계산
         */
        private long getWindowEnd() {
            return (windowStart.get() + 1) * config.getWindowSizeMs();
        }
    }
} 