package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.service.report.DeliveryReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/reports/delivery")
@ConditionalOnProperty(prefix = "app.delivery-report", name = "enabled", havingValue = "true")
public class DeliveryReportController {
    private final DeliveryReportService service;
    private final String token;

    public DeliveryReportController(DeliveryReportService service,
                                    @Value("${app.delivery-report.trigger-token:}") String token) {
        this.service = service;
        this.token = token;
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<DeliveryReportService.SendResult>> send(
            @RequestHeader(value = "X-Report-Token", required = false) String suppliedToken,
            @RequestParam(defaultValue = "false") boolean test) {
        if (!StringUtils.hasText(token)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail("手动发送令牌尚未配置"));
        }
        if (suppliedToken == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("手动发送令牌无效"));
        }
        return ResponseEntity.ok(ApiResponse.ok(service.sendToday(test)));
    }
}
