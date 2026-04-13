package com.example.demo.repository;

import com.example.demo.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
    Page<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<OrderEntity> findByOrderCode(String orderCode);
    Optional<OrderEntity> findByPaymentTransactionId(Long paymentTransactionId);
    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderEntity o WHERE o.status = 'DELIVERED'")
    BigDecimal sumDeliveredRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderEntity o WHERE o.status = 'DELIVERED' AND o.user.id = :userId")
    BigDecimal sumDeliveredRevenueByUser(@Param("userId") Long userId);

        @Query("SELECT o FROM OrderEntity o WHERE o.status = 'WAITING_PAYMENT' AND o.createdAt <= :expiredBefore")
        List<OrderEntity> findExpiredWaitingPaymentOrders(@Param("expiredBefore") LocalDateTime expiredBefore);

    @Query("""
            SELECT DISTINCT o FROM OrderEntity o
            LEFT JOIN FETCH o.items item
            LEFT JOIN FETCH item.product product
            LEFT JOIN FETCH product.category category
            WHERE o.status = 'DELIVERED'
            AND o.createdAt BETWEEN :fromDateTime AND :toDateTime
            ORDER BY o.createdAt ASC
            """)
    List<OrderEntity> findDeliveredOrdersWithItemsBetween(@Param("fromDateTime") LocalDateTime fromDateTime,
                                                          @Param("toDateTime") LocalDateTime toDateTime);
}
