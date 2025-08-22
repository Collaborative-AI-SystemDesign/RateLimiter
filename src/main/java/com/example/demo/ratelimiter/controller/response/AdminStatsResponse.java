package com.example.demo.ratelimiter.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 관리자 통계 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private Long userId; // 조회된 사용자 ID
    private String algorithm; // 조회된 알고리즘 이름
    private Map<String, Object> stats; // 알고리즘별 통계 정보 맵
    private LocalDateTime timestamp; // 응답 생성 시간
}
