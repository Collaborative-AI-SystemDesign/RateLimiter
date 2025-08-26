package com.example.demo.ratelimiter.algo.fixed;

import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisFixedWindowRateLimiter {

  private final RedisTemplate<String, String> redisTemplate;
  private final int limit = 10; // 임계치
  private final int windowSizeInSeconds = 60; // 윈도우 크기

  public RedisFixedWindowRateLimiter(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isAllowed(String key) {
    // 1. 카운터를 1 증가 (동시성 보장)
    Long count = redisTemplate.opsForValue().increment(key);

    // 2. 키가 처음 생성되었다면, 만료 시간 설정 (윈도우 리셋 자동화)
    if (count != null && count == 1) {
      redisTemplate.expire(key, Duration.ofSeconds(windowSizeInSeconds));
    }

    // 3. 임계치 초과 여부만 확인
    return count != null && count <= limit;
  }
}
