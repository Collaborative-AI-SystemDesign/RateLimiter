package com.example.demo.ratelimiter.window;

import java.util.Collections;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public class RedisSlidingWindowCounterLimiter {

//  private final RedisTemplate<String, String> redisTemplate;
//  private final RedisScript<Long> slidingWindowScript;
//
//  // 생성자에서 Lua 스크립트를 로드합니다.
//  public RedisSlidingWindowRateLimiter(RedisTemplate<String, String> redisTemplate) {
//    this.redisTemplate = redisTemplate;
//    // 실제로는 Lua 스크립트 파일을 로드하는 방식 사용
//    this.slidingWindowScript = RedisScript.of("... LUA SCRIPT TEXT ...", Long.class);
//  }
//
//  public boolean isAllowed(String key) {
//    long windowSize = 60000; // 1분 (ms)
//    long limit = 10;
//
//    // Redis에 Lua 스크립트 실행을 요청합니다.
//    Long result = redisTemplate.execute(
//        slidingWindowScript,
//        Collections.singletonList(key), // KEYS[1]
//        String.valueOf(System.currentTimeMillis()), // ARGV[1]
//        String.valueOf(windowSize),                 // ARGV[2]
//        String.valueOf(limit)                       // ARGV[3]
//    );
//
//    // 스크립트 결과(1 또는 0)에 따라 성공/실패를 반환합니다.
//    return result != null && result == 1;
//  }
}
