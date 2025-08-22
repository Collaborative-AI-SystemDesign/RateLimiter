package com.example.demo.ratelimiter.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 시간 관련 유틸리티 클래스
 */
public class TimeUtil {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    //현재 시간을 밀리초로 반환
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    //현재 시간을 초로 반환
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }
    
    //밀리초를 초로 변환
    public static long millisToSeconds(long millis) {
        return millis / 1000;
    }
    
    //초를 밀리초로 변환
    public static long secondsToMillis(long seconds) {
        return seconds * 1000;
    }
    
    //시간 차이를 초 단위로 계산
    public static double calculateElapsedSeconds(long startTimeMillis, long endTimeMillis) {
        return (endTimeMillis - startTimeMillis) / 1000.0;
    }
    
    //고정 윈도우의 현재 윈도우 시작 시간 계산
    public static long calculateWindowStart(long currentTimeMillis, long windowSizeMillis) {
        return (currentTimeMillis / windowSizeMillis) * windowSizeMillis;
    }
    
    //고정 윈도우의 다음 윈도우 시작 시간 계산
    public static long calculateNextWindowStart(long currentTimeMillis, long windowSizeMillis) {
        return calculateWindowStart(currentTimeMillis, windowSizeMillis) + windowSizeMillis;
    }
    
    //밀리초를 사람이 읽기 쉬운 형태로 변환
    public static String formatTimestamp(long timestampMillis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestampMillis), 
            ZoneId.systemDefault()
        );
        return dateTime.format(FORMATTER);
    }
    
    //재시도 권장 시간 계산 (초 단위)
    public static long calculateRetryAfterSeconds(long resetTimeMillis) {
        long currentTimeMillis = currentTimeMillis();
        if (resetTimeMillis <= currentTimeMillis) {
            return 0;
        }
        return (resetTimeMillis - currentTimeMillis) / 1000 + 1; // 1초 여유
    }
}
