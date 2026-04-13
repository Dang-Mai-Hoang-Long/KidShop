package com.example.demo.service;

import com.example.demo.dto.SepayWebhookPayload;
import com.example.demo.entity.*;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class OrderService {

    private static final String STATUS_WAITING_PAYMENT = "WAITING_PAYMENT";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String PAYMENT_METHOD_BANK = "BANK";
    private static final String SEPAY_TRANSFER_IN = "in";
    private static final int BANK_PAYMENT_TIMEOUT_MINUTES = 15;
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("\\bMWK\\d{16}\\b", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter SEPAY_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ALLOWED_STATUSES = Set.of("PENDING", "CONFIRMED", "SHIPPING", "DELIVERED", "CANCELLED");
    private static final BigDecimal BRONZE_THRESHOLD = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal SILVER_THRESHOLD = BigDecimal.valueOf(3_000_000L);
    private static final BigDecimal GOLD_THRESHOLD = BigDecimal.valueOf(5_000_000L);
    private static final BigDecimal DIAMOND_THRESHOLD = BigDecimal.valueOf(10_000_000L);

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserAccountRepository userAccountRepository;

    public OrderService(OrderRepository orderRepository, CartItemRepository cartItemRepository, ProductRepository productRepository, UserAccountRepository userAccountRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public OrderEntity placeOrder(Long userId, String recipientName, String recipientPhone, String shippingAddress, String note, String paymentMethod) {
        UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
        List<CartItemEntity> cartItems = cartItemRepository.findByUserIdOrderByIdDesc(userId);

        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Giỏ hàng trống");
        }

        OrderEntity order = new OrderEntity();
        order.setOrderCode(generateOrderCode());
        order.setUser(user);
        order.setRecipientName(recipientName.trim());
        order.setRecipientPhone(recipientPhone.trim());
        order.setShippingAddress(shippingAddress.trim());
        order.setNote(note != null ? note.trim() : null);
        String resolvedPaymentMethod = paymentMethod == null ? "COD" : paymentMethod.trim().toUpperCase();
        order.setPaymentMethod(resolvedPaymentMethod);
        order.setStatus(isBankPaymentMethod(resolvedPaymentMethod) ? STATUS_WAITING_PAYMENT : "PENDING");
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;

        for (CartItemEntity cartItem : cartItems) {
            ProductEntity product = cartItem.getProduct();
            if (product.getStock() < cartItem.getQuantity()) {
                throw new IllegalStateException("Sản phẩm \"" + product.getName() + "\" không đủ số lượng");
            }

            BigDecimal unitPrice = product.getActivePrice();
            if (unitPrice == null) {
                throw new IllegalStateException("Không thể xác định giá của sản phẩm \"" + product.getName() + "\"");
            }

            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(product.getImagePath());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setSelectedSize(cartItem.getSelectedSize());
            orderItem.setOrderCode(order.getOrderCode());
            orderItem.setProductCode(product.getProductCode());
            order.getItems().add(orderItem);

            total = total.add(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));

            product.setStock(product.getStock() - cartItem.getQuantity());
            product.setSoldCount(product.getSoldCount() + cartItem.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(total);
        OrderEntity saved = orderRepository.save(order);

        cartItemRepository.deleteByUserId(userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OrderEntity> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderEntity> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderEntity> searchOrders(String keyword, String status, Pageable pageable) {
        Specification<OrderEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim().toLowerCase();
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("orderCode")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("recipientName")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("recipientPhone")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("shippingAddress")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.join("user").get("account")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.join("user").get("email")), like(normalizedKeyword))
            ));
        }

        if (status != null && !status.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status.trim().toUpperCase()));
        }

        return orderRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public OrderEntity getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public LocalDateTime resolveBankPaymentDeadline(OrderEntity order) {
        if (order == null || order.getCreatedAt() == null) {
            return LocalDateTime.now();
        }
        return order.getCreatedAt().plusMinutes(BANK_PAYMENT_TIMEOUT_MINUTES);
    }

    @Transactional(readOnly = true)
    public long resolveBankPaymentRemainingSeconds(OrderEntity order) {
        if (!isWaitingBankPayment(order)) {
            return 0L;
        }

        long remaining = Duration.between(LocalDateTime.now(), resolveBankPaymentDeadline(order)).getSeconds();
        return Math.max(remaining, 0L);
    }

    @Transactional(readOnly = true)
    public boolean isBankPaymentPending(OrderEntity order) {
        return isWaitingBankPayment(order) && resolveBankPaymentRemainingSeconds(order) > 0;
    }

    public boolean expireBankPaymentIfNeeded(OrderEntity order) {
        if (!isWaitingBankPayment(order)) {
            return false;
        }
        if (resolveBankPaymentDeadline(order).isAfter(LocalDateTime.now())) {
            return false;
        }
        return cancelExpiredWaitingPaymentOrder(order);
    }

    public int expirePendingBankPayments() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(BANK_PAYMENT_TIMEOUT_MINUTES);
        List<OrderEntity> expiredOrders = orderRepository.findExpiredWaitingPaymentOrders(expiredBefore);

        int cancelledCount = 0;
        for (OrderEntity order : expiredOrders) {
            if (cancelExpiredWaitingPaymentOrder(order)) {
                cancelledCount++;
            }
        }
        return cancelledCount;
    }

    public BankPaymentWebhookResult processSepayWebhook(SepayWebhookPayload payload) {
        if (payload == null) {
            return BankPaymentWebhookResult.ignored("Payload webhook trống");
        }

        if (!SEPAY_TRANSFER_IN.equalsIgnoreCase(safeTrim(payload.getTransferType()))) {
            return BankPaymentWebhookResult.ignored("Giao dịch không phải tiền vào");
        }

        Optional<OrderEntity> existingByTransaction = findOrderByTransactionId(payload.getId());
        if (existingByTransaction.isPresent()) {
            OrderEntity matchedOrder = existingByTransaction.get();
            if (matchedOrder.isPaymentConfirmed()) {
                return BankPaymentWebhookResult.alreadyProcessed(matchedOrder, "Giao dịch đã được xác nhận trước đó");
            }
        }

        String orderCode = extractOrderCode(payload);
        if (orderCode == null || orderCode.isBlank()) {
            return BankPaymentWebhookResult.ignored("Không tìm thấy mã đơn hàng trong nội dung webhook");
        }

        OrderEntity order = orderRepository.findByOrderCode(orderCode).orElse(null);
        if (order == null) {
            return BankPaymentWebhookResult.ignored("Không tìm thấy đơn hàng tương ứng mã " + orderCode);
        }

        if (order.isPaymentConfirmed()) {
            return BankPaymentWebhookResult.alreadyProcessed(order, "Đơn hàng đã được xác nhận thanh toán");
        }

        if (!isWaitingBankPayment(order)) {
            return BankPaymentWebhookResult.ignored("Đơn hàng không ở trạng thái chờ thanh toán");
        }

        if (!isBankPaymentPending(order)) {
            expireBankPaymentIfNeeded(order);
            return BankPaymentWebhookResult.ignored("Đơn hàng đã hết hạn thanh toán");
        }

        BigDecimal expectedAmount = normalizeMoney(order.getTotalAmount());
        BigDecimal receivedAmount = normalizeMoney(payload.getTransferAmount());
        if (expectedAmount.compareTo(receivedAmount) != 0) {
            return BankPaymentWebhookResult.ignored("Số tiền chuyển khoản không khớp đơn hàng");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setPaymentConfirmed(true);
        order.setPaymentConfirmedAt(resolvePaymentTime(payload.getTransactionDate(), now));
        order.setPaymentGateway(safeTrim(payload.getGateway()));
        order.setPaymentReferenceCode(safeTrim(payload.getReferenceCode()));
        order.setPaymentTransactionId(payload.getId());
        order.setPaymentTransferAmount(receivedAmount);
        order.setStatus(STATUS_DELIVERED);
        order.setUpdatedAt(now);

        orderRepository.save(order);
        refreshUserRankByDeliveredOrders(order.getUser().getId());

        return BankPaymentWebhookResult.processed(order, "Xác nhận thanh toán thành công");
    }

    public void updateStatus(Long orderId, String newStatus) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();

        String currentStatus = normalizeStatus(order.getStatus());
        if (STATUS_WAITING_PAYMENT.equals(currentStatus)) {
            throw new IllegalStateException("Đơn hàng đang chờ thanh toán nên chưa thể thao tác từ trang quản trị.");
        }
        if (isTerminalStatus(currentStatus)) {
            throw new IllegalStateException("Đơn hàng đã ở trạng thái kết thúc và không thể chỉnh sửa thêm.");
        }

        String resolvedStatus = normalizeStatus(newStatus);
        if (!ALLOWED_STATUSES.contains(resolvedStatus)) {
            throw new IllegalStateException("Trạng thái đơn hàng không hợp lệ.");
        }

        if (currentStatus.equals(resolvedStatus)) {
            return;
        }

        order.setStatus(resolvedStatus);
        order.setUpdatedAt(LocalDateTime.now());
        OrderEntity savedOrder = orderRepository.save(order);

        if ("DELIVERED".equals(resolvedStatus)) {
            refreshUserRankByDeliveredOrders(savedOrder.getUser().getId());
        }
    }

    public void cancelOrder(Long orderId, Long userId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Không có quyền hủy đơn hàng này");
        }
        String currentStatus = normalizeStatus(order.getStatus());
        if (!"PENDING".equals(currentStatus) && !STATUS_WAITING_PAYMENT.equals(currentStatus)) {
            throw new IllegalStateException("Chỉ có thể hủy đơn hàng đang chờ xác nhận hoặc chờ thanh toán");
        }

        order.setStatus(STATUS_CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        restoreOrderInventory(order);
        orderRepository.save(order);
    }

    private boolean cancelExpiredWaitingPaymentOrder(OrderEntity order) {
        if (!isWaitingBankPayment(order)) {
            return false;
        }

        order.setStatus(STATUS_CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());

        String expiredReason = "Hệ thống tự hủy do quá 15 phút chưa thanh toán chuyển khoản.";
        if (order.getNote() == null || order.getNote().isBlank()) {
            order.setNote(expiredReason);
        } else if (!order.getNote().contains("quá 15 phút")) {
            order.setNote(order.getNote().trim() + " | " + expiredReason);
        }

        restoreOrderInventory(order);
        orderRepository.save(order);
        return true;
    }

    private void restoreOrderInventory(OrderEntity order) {
        for (OrderItemEntity item : order.getItems()) {
            ProductEntity product = item.getProduct();
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                product.setSoldCount(Math.max(0, product.getSoldCount() - item.getQuantity()));
                productRepository.save(product);
            }
        }
    }

    private boolean isWaitingBankPayment(OrderEntity order) {
        if (order == null) {
            return false;
        }
        return STATUS_WAITING_PAYMENT.equals(normalizeStatus(order.getStatus()))
                && isBankPaymentMethod(order.getPaymentMethod());
    }

    private boolean isBankPaymentMethod(String paymentMethod) {
        return PAYMENT_METHOD_BANK.equalsIgnoreCase(paymentMethod);
    }

    private Optional<OrderEntity> findOrderByTransactionId(Long transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        return orderRepository.findByPaymentTransactionId(transactionId);
    }

    private String extractOrderCode(SepayWebhookPayload payload) {
        String[] sources = {
                payload.getCode(),
                payload.getContent(),
                payload.getDescription()
        };

        for (String source : sources) {
            String resolved = findOrderCodeInText(source);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private String findOrderCodeInText(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        Matcher matcher = ORDER_CODE_PATTERN.matcher(source.toUpperCase());
        if (matcher.find()) {
            return matcher.group();
        }

        String normalized = source.trim().toUpperCase();
        return normalized.matches("MWK\\d{16}") ? normalized : null;
    }

    private LocalDateTime resolvePaymentTime(String transactionDate, LocalDateTime fallback) {
        if (transactionDate == null || transactionDate.isBlank()) {
            return fallback;
        }

        try {
            return LocalDateTime.parse(transactionDate.trim(), SEPAY_DATE_TIME_FORMATTER);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.stripTrailingZeros();
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Transactional(readOnly = true)
    public long countOrders() {
        return orderRepository.count();
    }

    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return orderRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDeliveredRevenue() {
        return orderRepository.sumDeliveredRevenue();
    }

    @Transactional(readOnly = true)
    public BigDecimal getDeliveredSpendByUser(Long userId) {
        BigDecimal deliveredSpend = orderRepository.sumDeliveredRevenueByUser(userId);
        return deliveredSpend == null ? BigDecimal.ZERO : deliveredSpend;
    }

    @Transactional(readOnly = true)
    public BigDecimal getEffectiveSpendByUser(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
        return getEffectiveSpendByUser(user);
    }

    @Transactional(readOnly = true)
    public BigDecimal getEffectiveSpendByUser(UserAccountEntity user) {
        if (user == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal deliveredSpend = getDeliveredSpendByUser(user.getId());
        return resolveEffectiveSpend(deliveredSpend, user.getPurchaseSpendOffset());
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getRecentOrders(Long userId, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 20));
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, resolvedLimit)).getContent();
    }

    public CurrentUser refreshUserRankByDeliveredOrders(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
        BigDecimal deliveredSpend = getDeliveredSpendByUser(userId);
        BigDecimal effectiveSpend = resolveEffectiveSpend(deliveredSpend, user.getPurchaseSpendOffset());
        String resolvedRank = resolveRankByDeliveredSpend(effectiveSpend);

        if (user.getRank() == null || !resolvedRank.equalsIgnoreCase(user.getRank())) {
            user.setRank(resolvedRank);
            user = userAccountRepository.saveAndFlush(user);
        }

        return CurrentUser.from(user);
    }

    @Transactional(readOnly = true)
    public RankProgress buildRankProgress(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
        BigDecimal deliveredSpend = getDeliveredSpendByUser(userId);
        BigDecimal effectiveSpend = resolveEffectiveSpend(deliveredSpend, user.getPurchaseSpendOffset());
        String currentRank = resolveRankByDeliveredSpend(effectiveSpend);
        String nextRank = nextRank(currentRank);
        BigDecimal nextThreshold = thresholdForRank(nextRank);
        BigDecimal floorThreshold = progressFloorForRank(currentRank);

        BigDecimal remainingToNext = nextThreshold == null
                ? BigDecimal.ZERO
            : nextThreshold.subtract(effectiveSpend).max(BigDecimal.ZERO);

        int progressPercent = calculateProgressPercent(effectiveSpend, floorThreshold, nextThreshold);

        return new RankProgress(
                currentRank,
                rankLabel(currentRank),
                rankCssClass(currentRank),
            formatCurrency(effectiveSpend),
                nextRank,
                rankLabel(nextRank),
                rankCssClass(nextRank),
                formatCurrency(remainingToNext),
                progressPercent,
                nextRank == null
        );
    }

        private BigDecimal resolveEffectiveSpend(BigDecimal deliveredSpend, BigDecimal purchaseSpendOffset) {
        BigDecimal delivered = deliveredSpend == null ? BigDecimal.ZERO : deliveredSpend;
        BigDecimal offset = purchaseSpendOffset == null ? BigDecimal.ZERO : purchaseSpendOffset;
        return delivered.add(offset).max(BigDecimal.ZERO);
        }

    private String resolveRankByDeliveredSpend(BigDecimal deliveredSpend) {
        BigDecimal spend = deliveredSpend == null ? BigDecimal.ZERO : deliveredSpend;

        if (spend.compareTo(DIAMOND_THRESHOLD) >= 0) {
            return "DIAMOND";
        }
        if (spend.compareTo(GOLD_THRESHOLD) >= 0) {
            return "GOLD";
        }
        if (spend.compareTo(SILVER_THRESHOLD) >= 0) {
            return "SILVER";
        }
        return "BRONZE";
    }

    private String nextRank(String rank) {
        if (rank == null) {
            return "SILVER";
        }

        return switch (rank.toUpperCase()) {
            case "BRONZE" -> "SILVER";
            case "SILVER" -> "GOLD";
            case "GOLD" -> "DIAMOND";
            default -> null;
        };
    }

    private BigDecimal thresholdForRank(String rank) {
        if (rank == null) {
            return null;
        }

        return switch (rank.toUpperCase()) {
            case "BRONZE" -> BRONZE_THRESHOLD;
            case "SILVER" -> SILVER_THRESHOLD;
            case "GOLD" -> GOLD_THRESHOLD;
            case "DIAMOND" -> DIAMOND_THRESHOLD;
            default -> null;
        };
    }

    private BigDecimal progressFloorForRank(String rank) {
        if (rank == null) {
            return BigDecimal.ZERO;
        }

        return switch (rank.toUpperCase()) {
            case "SILVER" -> SILVER_THRESHOLD;
            case "GOLD" -> GOLD_THRESHOLD;
            case "DIAMOND" -> DIAMOND_THRESHOLD;
            default -> BigDecimal.ZERO;
        };
    }

    private int calculateProgressPercent(BigDecimal deliveredSpend, BigDecimal floorThreshold, BigDecimal nextThreshold) {
        if (nextThreshold == null) {
            return 100;
        }

        BigDecimal floor = floorThreshold == null ? BigDecimal.ZERO : floorThreshold;
        BigDecimal spend = deliveredSpend == null ? BigDecimal.ZERO : deliveredSpend;
        BigDecimal denominator = nextThreshold.subtract(floor);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return 100;
        }

        BigDecimal boundedSpend = spend.max(floor).min(nextThreshold);
        BigDecimal numerator = boundedSpend.subtract(floor);
        int percent = numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
        return Math.max(0, Math.min(percent, 100));
    }

    private String rankLabel(String rank) {
        if (rank == null) {
            return "Đồng";
        }

        return switch (rank.toUpperCase()) {
            case "SILVER" -> "Bạc";
            case "GOLD" -> "Vàng";
            case "DIAMOND" -> "Kim cương";
            default -> "Đồng";
        };
    }

    private String rankCssClass(String rank) {
        if (rank == null) {
            return "rank-bronze";
        }

        return switch (rank.toUpperCase()) {
            case "SILVER" -> "rank-silver";
            case "GOLD" -> "rank-gold";
            case "DIAMOND" -> "rank-diamond";
            default -> "rank-bronze";
        };
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return String.format("%,.0f", value) + "đ";
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private boolean isTerminalStatus(String status) {
        return STATUS_DELIVERED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    public static class BankPaymentWebhookResult {
        private final boolean processed;
        private final boolean alreadyProcessed;
        private final String message;
        private final OrderEntity order;

        private BankPaymentWebhookResult(boolean processed, boolean alreadyProcessed, String message, OrderEntity order) {
            this.processed = processed;
            this.alreadyProcessed = alreadyProcessed;
            this.message = message;
            this.order = order;
        }

        public static BankPaymentWebhookResult processed(OrderEntity order, String message) {
            return new BankPaymentWebhookResult(true, false, message, order);
        }

        public static BankPaymentWebhookResult alreadyProcessed(OrderEntity order, String message) {
            return new BankPaymentWebhookResult(false, true, message, order);
        }

        public static BankPaymentWebhookResult ignored(String message) {
            return new BankPaymentWebhookResult(false, false, message, null);
        }

        public boolean isProcessed() {
            return processed;
        }

        public boolean isAlreadyProcessed() {
            return alreadyProcessed;
        }

        public String getMessage() {
            return message;
        }

        public OrderEntity getOrder() {
            return order;
        }
    }

    public static class RankProgress {
        private final String currentRank;
        private final String currentRankLabel;
        private final String currentRankClass;
        private final String deliveredSpendFormatted;
        private final String nextRank;
        private final String nextRankLabel;
        private final String nextRankClass;
        private final String remainingToNextFormatted;
        private final int progressPercent;
        private final boolean topTier;

        public RankProgress(String currentRank,
                            String currentRankLabel,
                            String currentRankClass,
                            String deliveredSpendFormatted,
                            String nextRank,
                            String nextRankLabel,
                            String nextRankClass,
                            String remainingToNextFormatted,
                            int progressPercent,
                            boolean topTier) {
            this.currentRank = currentRank;
            this.currentRankLabel = currentRankLabel;
            this.currentRankClass = currentRankClass;
            this.deliveredSpendFormatted = deliveredSpendFormatted;
            this.nextRank = nextRank;
            this.nextRankLabel = nextRankLabel;
            this.nextRankClass = nextRankClass;
            this.remainingToNextFormatted = remainingToNextFormatted;
            this.progressPercent = progressPercent;
            this.topTier = topTier;
        }

        public String getCurrentRank() {
            return currentRank;
        }

        public String getCurrentRankLabel() {
            return currentRankLabel;
        }

        public String getCurrentRankClass() {
            return currentRankClass;
        }

        public String getDeliveredSpendFormatted() {
            return deliveredSpendFormatted;
        }

        public String getNextRank() {
            return nextRank;
        }

        public String getNextRankLabel() {
            return nextRankLabel;
        }

        public String getNextRankClass() {
            return nextRankClass;
        }

        public String getRemainingToNextFormatted() {
            return remainingToNextFormatted;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public boolean isTopTier() {
            return topTier;
        }
    }

    private String generateOrderCode() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "MWK" + timestamp + random;
    }

    private String like(String value) {
        return "%" + value + "%";
    }
}
