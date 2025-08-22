package com.example.demo.ratelimiter.controller.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 통계 조회 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsRequest {

    @NotNull
    private Long userId; // 조회할 사용자 ID

    @NotNull
    private String algorithm; // 조회할 알고리즘 이름
}
