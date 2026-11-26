package com.campusoverflow.reputation.contract;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 契约测试用异常翻译：非法枚举参数（{@link IllegalArgumentException}）映射为 400，
 * 与生产 GlobalExceptionHandler 的行为保持一致；standaloneSetup 下不会自动加载生产 advice，故在此补齐。
 */
@RestControllerAdvice
public class ContractTestExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "SYS-400", "message", "请求参数不合法"));
    }
}
