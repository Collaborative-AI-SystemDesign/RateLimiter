package com.example.demo.ratelimiter.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알고리즘 테스트 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmTestResponse {

    private String algorithm; // 알고리즘 이름
    private String message; // 응답 메시지
    private String description; // 알고리즘 설명
    private String endpoint; // 엔드포인트 경로
    private Long userId; // 요청을 보낸 사용자 ID
    private LocalDateTime timestamp; // 응답 생성 시간

    public static AlgorithmTestResponse of(String algorithm, String message, String description, String endpoint, Long userId) {
        return AlgorithmTestResponse.builder()
                .algorithm(algorithm)
                .message(message)
                .description(description)
                .endpoint(endpoint)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
