package com.example.demo.controller;

import com.example.demo.dto.SepayWebhookPayload;
import com.example.demo.entity.OrderEntity;
import com.example.demo.service.AuditService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PaymentWebhookController {

    private static final String AUTHORIZATION_PREFIX = "Apikey ";

    private final OrderService orderService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final String sepayWebhookApiKey;

    public PaymentWebhookController(OrderService orderService,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    @Value("${sepay.webhook.api-key:}") String sepayWebhookApiKey) {
        this.orderService = orderService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.sepayWebhookApiKey = sepayWebhookApiKey;
    }

    @PostMapping("/webhooks/sepay")
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) SepayWebhookPayload payload) {

        if (!isAuthorized(authorization)) {
            Map<String, Object> unauthorized = new LinkedHashMap<>();
            unauthorized.put("success", false);
            unauthorized.put("processed", false);
            unauthorized.put("alreadyProcessed", false);
            unauthorized.put("message", "Unauthorized: API key không hợp lệ");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(unauthorized);
        }

        OrderService.BankPaymentWebhookResult result = orderService.processSepayWebhook(payload);

        if (result.isProcessed() && result.getOrder() != null) {
            OrderEntity order = result.getOrder();
            String actionDetail = "Webhook SePay xác nhận thanh toán thành công cho đơn #"
                    + order.getOrderCode()
                    + " | gateway=" + (order.getPaymentGateway() == null ? "N/A" : order.getPaymentGateway())
                    + " | ref=" + (order.getPaymentReferenceCode() == null ? "N/A" : order.getPaymentReferenceCode());

            auditService.log("SYSTEM", "SEPAY_WEBHOOK_PAID", actionDetail);
            notificationService.notifyUserAndAdmins(
                    order.getUser().getId(),
                    "Đơn hàng #" + order.getOrderCode() + " đã thanh toán",
                    "Thanh toán chuyển khoản đã được xác thực. Đơn hàng được cập nhật sang trạng thái Đã giao.",
                    "/orders/" + order.getId(),
                    order.getId(),
                    "ORDER_BANK_PAID");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isProcessed() || result.isAlreadyProcessed());
        response.put("processed", result.isProcessed());
        response.put("alreadyProcessed", result.isAlreadyProcessed());
        response.put("message", result.getMessage());

        if (result.getOrder() != null) {
            response.put("orderCode", result.getOrder().getOrderCode());
            response.put("orderStatus", result.getOrder().getStatus());
        }

        return ResponseEntity.ok(response);
    }

    private boolean isAuthorized(String authorization) {
        if (sepayWebhookApiKey == null || sepayWebhookApiKey.isBlank()) {
            return false;
        }

        if (authorization == null || authorization.isBlank()) {
            return false;
        }

        if (!authorization.regionMatches(true, 0, AUTHORIZATION_PREFIX, 0, AUTHORIZATION_PREFIX.length())) {
            return false;
        }

        String providedApiKey = authorization.substring(AUTHORIZATION_PREFIX.length()).trim();
        return sepayWebhookApiKey.equals(providedApiKey);
    }
}
