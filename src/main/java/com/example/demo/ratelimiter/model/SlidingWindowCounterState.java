package com.example.demo.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding Window Counter에서 사용되는 카운터 상태 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlidingWindowCounterState {
    private Map<Long, Long> windowCounts; // 윈도우별 요청 카운트 (windowStart -> count)
    private int limit; // 윈도우 내 최대 요청 수
    private long windowSizeMillis; // 윈도우 크기 (밀리초)
    private long subWindowSizeMillis; // 서브 윈도우 크기 (밀리초)

    public static SlidingWindowCounterState createSlidingWindowCounter(int limit, long windowSizeSeconds, int subWindowCount) {
        long windowSizeMillis = windowSizeSeconds * 1000;
        long subWindowSizeMillis = windowSizeMillis / subWindowCount;
        return new SlidingWindowCounterState(new ConcurrentHashMap<>(), limit, windowSizeMillis, subWindowSizeMillis);
    }
}
