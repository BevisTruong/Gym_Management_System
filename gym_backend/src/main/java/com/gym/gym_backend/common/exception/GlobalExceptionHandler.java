package com.gym.gym_backend.common.exception;

import com.gym.gym_backend.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> accessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Không có quyền truy cập"));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors())
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "Dữ liệu không hợp lệ", "errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<?> constraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Đã xảy ra lỗi hệ thống"));
    }

}
