package com.example.demo.ratelimiter.algo;

import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisSlidingWindowLogRateLimiter {

  private final RedisTemplate<String, String> redisTemplate;
  private static final int LIMIT = 10;
  private static final long WINDOW_SIZE_MS = 60 * 1000; // 1분

  public RedisSlidingWindowLogRateLimiter(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isAllowed(String key) {
    long now = System.currentTimeMillis();
    long windowStart = now - WINDOW_SIZE_MS;

    // 1. 윈도우를 벗어난 오래된 로그를 한 번에 삭제 (cleanupOldRequests 대체)
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

    // 2. 현재 윈도우 내의 요청 수를 한 번에 확인 (requestLog.size() 대체)
    Long count = redisTemplate.opsForZSet().zCard(key);

    // 3. 한도 확인
    if (count != null && count >= LIMIT) {
      return false; // 거부
    }

    // 4. 현재 요청을 로그에 추가 (requestLog.offer() 대체)
    redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), now);

    return true; // 허용
  }
}
