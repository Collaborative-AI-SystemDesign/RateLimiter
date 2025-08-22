package com.example.demo.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fixed Window Counter에서 사용되는 윈도우 상태 클래스
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FixedWindowState {
    private long requestCount; // 현재 윈도우 내 요청 수
    private long windowStartTime; // 윈도우 시작 시간
    private int limit; // 윈도우 내 최대 요청 수
    private long windowSizeMillis; // 윈도우 크기 (밀리초)

    public static FixedWindowState createFixedWindow(int limit, long windowSizeSeconds) {
        long currentTime = System.currentTimeMillis();
        long windowSizeMillis = windowSizeSeconds * 1000;
        long windowStartTime = (currentTime / windowSizeMillis) * windowSizeMillis;
        return new FixedWindowState(0, windowStartTime, limit, windowSizeMillis);
    }
}
