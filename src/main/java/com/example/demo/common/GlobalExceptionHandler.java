package com.example.demo.common;

import com.example.demo.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // TODO: 필요 시 핸들러 추가

    // 1. 사용자 정의 예외 처리 (예: CustomException)
    // CustomException에 HttpStatus 필드를 추가하여 유연하게 처리 가능
    // 예: new CustomException("에러 발생", HttpStatus.BAD_REQUEST);
//    @ExceptionHandler(CustomException.class)
//    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
//        HttpStatus status = ex.getHttpStatus() != null ? ex.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
//        return new ResponseEntity<>(ApiResponse.fail(ex.getMessage()), status);
//    }

    // 2. @Valid 어노테이션 유효성 검사 실패 시 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ResponseEntity<>(ApiResponse.fail("Validation failed: " + errorMessage), HttpStatus.BAD_REQUEST);
    }

    // 3. 404 Not Found 예외 처리 (설정 필요)
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        return new ResponseEntity<>(ApiResponse.fail("Resource not found: " + ex.getRequestURL()), HttpStatus.NOT_FOUND);
    }

    // 4. 모든 예상치 못한 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {
        return new ResponseEntity<>(ApiResponse.fail("An unexpected error occurred: " + ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
