package com.example.demo.scheduler;

import com.example.demo.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BankPaymentScheduler {

    private final OrderService orderService;

    public BankPaymentScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cancelExpiredWaitingPayments() {
        orderService.expirePendingBankPayments();
    }
}
