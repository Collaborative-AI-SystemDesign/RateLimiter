package com.example.demo.application;

import com.example.demo.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {

    @GetMapping("/health")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.TOKEN_BUCKET,
        limit = 20,
        refillRate = 5,
        message = "헬스체크 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
    )
    public String healthCheck() {
        return "Application is running";
    }
    
    @GetMapping("/")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.FIXED_WINDOW,
        limit = 100,
        windowSeconds = 60,
        message = "메인 페이지 접근 제한 초과! 1분 후 다시 시도해주세요."
    )
    public String home() {
        return "🚀 Rate Limiter Demo - 수동 구현 vs 라이브러리 비교\n\n" +
               "📚 수동 구현 테스트 (Manual Implementation):\n" +
               "- GET /api/rate-limit/test/token-bucket\n" +
               "- GET /api/rate-limit/test/leaky-bucket\n" +
               "- GET /api/rate-limit/test/fixed-window\n" +
               "- GET /api/rate-limit/test/sliding-log\n" +
               "- GET /api/rate-limit/test/sliding-counter\n" +
               "- GET /api/rate-limit/algorithms\n\n" +
               "🏗️ 라이브러리 기반 테스트 (Library Implementation):\n" +
               "- GET /api/library-rate-limit/test/bucket4j\n" +
               "- GET /api/library-rate-limit/test/resilience4j\n" +
               "- GET /api/library-rate-limit/test/guava\n" +
               "- GET /api/library-rate-limit/libraries\n" +
               "- GET /api/library-rate-limit/comparison\n\n" +
               "⚖️ 비교 및 벤치마크:\n" +
               "- GET /benchmark/comparison\n" +
               "- GET /benchmark/performance";
    }
    
    @GetMapping("/library-demo")
    @RateLimit(
        algorithm = RateLimit.AlgorithmType.TOKEN_BUCKET,
        limit = 50,
        refillRate = 10,
        message = "라이브러리 기반 제한 초과! Token Bucket으로 제한되었습니다."
    )
    public String libraryDemo() {
        return "🪣 Redis 기반 Rate Limiting 데모!\n\n" +
               "이 엔드포인트는 Redis Token Bucket 알고리즘으로 제한됩니다.\n" +
               "- 1분당 50개 요청 허용, 초당 10개씩 보충\n" +
               "- 분산 환경에서 정확한 제한\n" +
               "- 프로덕션 준비 완료";
    }
}
