package com.example.demo.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Sliding Window Log에서 사용되는 로그 상태 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlidingWindowLogState {
    private Queue<Long> requestLog; // 요청 타임스탬프 로그
    private int limit; // 윈도우 내 최대 요청 수
    private long windowSizeMillis; // 윈도우 크기 (밀리초)

    public static SlidingWindowLogState createSlidingWindowLog(int limit, long windowSizeSeconds) {
        long windowSizeMillis = windowSizeSeconds * 1000;
        return new SlidingWindowLogState(new LinkedList<>(), limit, windowSizeMillis);
    }
}
