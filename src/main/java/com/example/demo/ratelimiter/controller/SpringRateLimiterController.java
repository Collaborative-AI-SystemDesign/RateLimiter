package com.example.demo.ratelimiter.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/dependency")
public class SpringRateLimiterController {

  // 1. Rate Limiter 적용
  @GetMapping("/fixed-window")
  @RateLimiter(name = "fixedWindow", fallbackMethod = "apiFallback")
  public ResponseEntity<String> fixedWindow() {
    // 비즈니스 로직 (요청이 허용되었을 때만 실행)
    return ResponseEntity.ok("API 요청 처리 성공!");
  }

  // 2. Fallback 메소드 (요청이 거부되었을 때 실행)
  public ResponseEntity<String> apiFallback(Throwable t) {
    // HTTP 429 Too Many Requests 상태 코드와 함께 에러 메시지 반환
    return new ResponseEntity<>("너무 많은 요청입니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS);
  }
}
