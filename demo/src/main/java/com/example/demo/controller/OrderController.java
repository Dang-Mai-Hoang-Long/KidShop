package com.example.demo.controller;

import com.example.demo.entity.CartItemEntity;
import com.example.demo.entity.OrderEntity;
import com.example.demo.entity.ShippingAddressEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.AuditService;
import com.example.demo.service.CartService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.OrderService;
import com.example.demo.service.ShippingAddressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    private static final String PAYMENT_METHOD_BANK = "BANK";
    private static final String BANK_NAME = "VPBank";
    private static final String BANK_ACCOUNT_NUMBER = "0386939650";
    private static final String VIET_QR_TEMPLATE = "https://api.vietqr.io/image/970432-0386939650-jgmz0pA.jpg";
    private static final String VIET_QR_ACCOUNT_NAME = "LE TAN PHONG";
    private static final DateTimeFormatter PAYMENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final OrderService orderService;
    private final CartService cartService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ShippingAddressService shippingAddressService;

    public OrderController(OrderService orderService, CartService cartService, AuditService auditService, NotificationService notificationService, ShippingAddressService shippingAddressService) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.shippingAddressService = shippingAddressService;
    }

    @GetMapping("/checkout")
    public String checkout(Model model, @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<CartItemEntity> items = cartService.getCartItems(currentUser.getId());
        if (items.isEmpty()) {
            return "redirect:/cart";
        }

        loadCheckoutModel(model, currentUser, items);
        model.addAttribute("bankPaymentPending", false);
        return "checkout";
    }

    @GetMapping({"/checkout/bank/{id}", "/bank/{id}"})
    public String bankCheckout(
            @PathVariable("id") Long orderId,
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        OrderEntity order = orderService.getOrder(orderId);
        if (!order.getUser().getId().equals(currentUser.getId()) && !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            redirectAttributes.addFlashAttribute("checkoutError", "Không có quyền truy cập đơn hàng này.");
            return "redirect:/orders";
        }

        if (orderService.expireBankPaymentIfNeeded(order)) {
            redirectAttributes.addFlashAttribute("checkoutError", "Đơn hàng đã quá hạn 15 phút và được hủy tự động.");
            return "redirect:/orders/" + orderId;
        }

        order = orderService.getOrder(orderId);

        if (!PAYMENT_METHOD_BANK.equalsIgnoreCase(order.getPaymentMethod())) {
            return "redirect:/orders/" + order.getId();
        }

        if (!orderService.isBankPaymentPending(order)) {
            if (order.isPaymentConfirmed()) {
                return "redirect:/orders/" + order.getId() + "?paymentSuccess=true";
            }
            return "redirect:/orders/" + order.getId() + "?paymentExpired=true";
        }

        model.addAttribute("bankPaymentPending", true);
        model.addAttribute("bankOrder", order);
        model.addAttribute("bankQrUrl", buildVietQrImageUrl(order.getTotalAmount(), order.getOrderCode()));
        model.addAttribute("bankPaymentRemainingSeconds", orderService.resolveBankPaymentRemainingSeconds(order));
        model.addAttribute("bankPaymentDeadlineAt", orderService.resolveBankPaymentDeadline(order));
        populateBankTransferInfo(model, order.getOrderCode());

        return "checkout";
    }

    @PostMapping("/checkout")
    public String placeOrder(
            @RequestParam("recipientName") String recipientName,
            @RequestParam("recipientPhone") String recipientPhone,
            @RequestParam("shippingAddress") String shippingAddress,
            @RequestParam(value = "savedAddressId", required = false) Long savedAddressId,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "paymentMethod", defaultValue = "COD") String paymentMethod,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            if (savedAddressId != null) {
                ShippingAddressEntity savedAddress = shippingAddressService.getForUser(savedAddressId, currentUser.getId());
                recipientName = savedAddress.getRecipientName();
                recipientPhone = savedAddress.getRecipientPhone();
                shippingAddress = savedAddress.getAddressLine();
            }

            String resolvedPaymentMethod = paymentMethod == null ? "COD" : paymentMethod.trim().toUpperCase();
            OrderEntity order = orderService.placeOrder(currentUser.getId(), recipientName, recipientPhone, shippingAddress, note, resolvedPaymentMethod);
            auditService.log(currentUser.getAccount(), "ĐẶT HÀNG",
                    "Đơn hàng #" + order.getOrderCode() + " | " + resolvedPaymentMethod + " | " + recipientName + " | " + shippingAddress);
            notificationService.notifyUserAndAdmins(
                currentUser.getId(),
                "Đơn hàng mới #" + order.getOrderCode(),
                "Đơn hàng #" + order.getOrderCode() + " đã được tạo thành công.",
                "/orders/" + order.getId(),
                order.getId(),
                "ORDER_CREATED");

            if (PAYMENT_METHOD_BANK.equalsIgnoreCase(resolvedPaymentMethod)) {
                redirectAttributes.addFlashAttribute("orderSuccess", "Đã tạo đơn hàng " + order.getOrderCode() + ". Vui lòng quét mã QR để chuyển khoản.");
                return "redirect:/checkout/bank/" + order.getId();
            }

            redirectAttributes.addFlashAttribute("orderSuccess", "Đặt hàng thành công! Mã đơn hàng: " + order.getOrderCode());
            return "redirect:/orders/" + order.getId();
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("checkoutError", ex.getMessage());
            return "redirect:/checkout";
        }
    }

    @GetMapping("/orders")
    public String myOrders(
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        orderService.expirePendingBankPayments();

        Page<OrderEntity> orderPage = orderService.getUserOrders(currentUser.getId(), PageRequest.of(Math.max(page, 0), 10));
        model.addAttribute("orderPage", orderPage);
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(
            @PathVariable("id") Long id,
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "paymentSuccess", required = false) Boolean paymentSuccess,
            @RequestParam(value = "paymentExpired", required = false) Boolean paymentExpired) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        OrderEntity order = orderService.getOrder(id);
        if (!order.getUser().getId().equals(currentUser.getId()) && !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return "redirect:/orders";
        }

        if (orderService.expireBankPaymentIfNeeded(order)) {
            order = orderService.getOrder(id);
            model.addAttribute("orderError", "Đơn hàng đã quá hạn 15 phút và được hủy tự động.");
        }

        if (Boolean.TRUE.equals(paymentSuccess)) {
            model.addAttribute("orderSuccess", "Thanh toán thành công! Đơn hàng đã được cập nhật.");
        }

        if (Boolean.TRUE.equals(paymentExpired)) {
            model.addAttribute("orderError", "Đơn hàng đã hết thời gian thanh toán hoặc đã được hủy.");
        }

        model.addAttribute("order", order);
        boolean bankPaymentPending = orderService.isBankPaymentPending(order);
        model.addAttribute("bankPaymentPending", bankPaymentPending);
        if (bankPaymentPending) {
            model.addAttribute("bankQrUrl", buildVietQrImageUrl(order.getTotalAmount(), order.getOrderCode()));
            model.addAttribute("bankPaymentRemainingSeconds", orderService.resolveBankPaymentRemainingSeconds(order));
            model.addAttribute("bankPaymentDeadlineAt", orderService.resolveBankPaymentDeadline(order));
            populateBankTransferInfo(model, order.getOrderCode());
        }
        return "order-detail";
    }

    @GetMapping("/orders/{id}/payment-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> paymentStatus(
            @PathVariable("id") Long id,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {

        Map<String, Object> payload = new LinkedHashMap<>();
        if (currentUser == null) {
            payload.put("success", false);
            payload.put("message", "Unauthorized");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(payload);
        }

        OrderEntity order = orderService.getOrder(id);
        boolean isOwner = order.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            payload.put("success", false);
            payload.put("message", "Forbidden");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(payload);
        }

        if (orderService.expireBankPaymentIfNeeded(order)) {
            order = orderService.getOrder(id);
        }

        boolean bankPaymentPending = orderService.isBankPaymentPending(order);

        payload.put("success", true);
        payload.put("orderId", order.getId());
        payload.put("orderCode", order.getOrderCode());
        payload.put("status", order.getStatus());
        payload.put("statusLabel", order.getStatusLabel());
        payload.put("statusColor", order.getStatusColor());
        payload.put("paymentMethod", order.getPaymentMethod());
        payload.put("bankTransfer", order.isBankTransfer());
        payload.put("paymentConfirmed", order.isPaymentConfirmed());
        payload.put("paymentStatusLabel", order.getPaymentStatusLabel());
        payload.put("paymentConfirmedAtText", order.getPaymentConfirmedAt() != null ? order.getPaymentConfirmedAt().format(PAYMENT_TIME_FORMATTER) : null);
        payload.put("bankPaymentPending", bankPaymentPending);
        payload.put("remainingSeconds", orderService.resolveBankPaymentRemainingSeconds(order));
        payload.put("redirectUrl", "/orders/" + order.getId());

        return ResponseEntity.ok(payload);
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(
            @PathVariable("id") Long id,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            orderService.cancelOrder(id, currentUser.getId());
            OrderEntity order = orderService.getOrder(id);
            auditService.log(currentUser.getAccount(), "HỦY ĐƠN HÀNG", "Hủy đơn hàng #" + order.getOrderCode());
            notificationService.notifyUserAndAdmins(
                    currentUser.getId(),
                    "Đơn hàng đã hủy #" + order.getOrderCode(),
                    "Đơn hàng #" + order.getOrderCode() + " đã được hủy.",
                    "/orders/" + order.getId(),
                    order.getId(),
                    "ORDER_CANCELLED");
            redirectAttributes.addFlashAttribute("orderSuccess", "Đã hủy đơn hàng thành công.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("orderError", ex.getMessage());
        }

        return "redirect:/orders/" + id;
    }

    private void loadCheckoutModel(Model model, CurrentUser currentUser, List<CartItemEntity> items) {
        BigDecimal total = cartService.getCartTotal(currentUser.getId());
        model.addAttribute("cartItems", items);
        model.addAttribute("cartTotal", total);
        model.addAttribute("formattedTotal", String.format("%,.0f", total) + "đ");
        model.addAttribute("shippingAddresses", shippingAddressService.listByUser(currentUser.getId()));
        model.addAttribute("defaultShippingAddress", shippingAddressService.getDefaultForUser(currentUser.getId()));
        model.addAttribute("checkoutRecipientName", currentUser.getDisplayName());
    }

    private String buildVietQrImageUrl(BigDecimal amount, String orderCode) {
        BigDecimal resolvedAmount = (amount == null ? BigDecimal.ZERO : amount).max(BigDecimal.ZERO);
        String normalizedAmount = resolvedAmount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        String encodedAccountName = URLEncoder.encode(VIET_QR_ACCOUNT_NAME, StandardCharsets.UTF_8);
        String encodedAddInfo = URLEncoder.encode(orderCode == null ? "" : orderCode, StandardCharsets.UTF_8);

        return VIET_QR_TEMPLATE
                + "?accountName=" + encodedAccountName
            + "&amount=" + normalizedAmount
                + "&addInfo=" + encodedAddInfo;
    }

    private void populateBankTransferInfo(Model model, String orderCode) {
        model.addAttribute("bankName", BANK_NAME);
        model.addAttribute("bankAccountName", VIET_QR_ACCOUNT_NAME);
        model.addAttribute("bankAccountNumber", BANK_ACCOUNT_NUMBER);
        model.addAttribute("bankTransferContent", orderCode == null ? "" : orderCode);
    }
}
