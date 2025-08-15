package com.example.demo.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;

/**
 * 모든 API 응답의 공통 형식을 정의하는 래퍼 클래스
 * @param <T> 응답 데이터의 타입
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success; // 요청 성공 여부
    private String message;  // 응답 메시지 (성공 또는 오류 메시지)
    private T data;          // 실제 응답 데이터

    /**
     * 성공 응답을 생성하는 정적 팩토리 메서드 (데이터 포함)
     * @param data 응답할 실제 데이터
     * @param <T> 데이터 타입
     * @return 성공 ApiResponse 객체
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    /**
     * 성공 응답을 생성하는 정적 팩토리 메서드 (메시지 포함, 데이터 없음)
     * @param message 응답 메시지
     * @param <T> 데이터 타입
     * @return 성공 ApiResponse 객체
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /**
     * 성공 응답을 생성하는 정적 팩토리 메서드 (데이터 및 메시지 포함)
     * @param message 응답 메시지
     * @param data 응답할 실제 데이터
     * @param <T> 데이터 타입
     * @return 성공 ApiResponse 객체
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * 실패 응답을 생성하는 정적 팩토리 메서드 (오류 메시지 포함)
     * @param message 오류 메시지
     * @param <T> 데이터 타입
     * @return 실패 ApiResponse 객체
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
