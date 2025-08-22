package com.example.demo.ratelimiter.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 관리자 리셋 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminResetResponse {
    private String message; // 응답 메시지
    private Long userId; // 조회된 사용자 ID
    private Map<String, String> resetResults; // 알고리즘별 리셋 결과 맵
    private LocalDateTime timestamp; // 응답 생성 시간
}
