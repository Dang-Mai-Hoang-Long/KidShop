package com.example.demo.controller;

import com.example.demo.entity.NotificationEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public String notifications(
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        int safePage = Math.max(page, 0);
        model.addAttribute("notificationPage", notificationService.getNotificationPage(
                currentUser.getId(),
                PageRequest.of(safePage, 10, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("notificationUnreadCount", notificationService.countUnread(currentUser.getId()));
        model.addAttribute("recentNotifications", notificationService.getRecentNotifications(currentUser.getId(), 10));
        return "notifications";
    }

    @GetMapping("/notifications/{id}/open")
    public String openNotification(
            @PathVariable("id") Long id,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        NotificationEntity notification = notificationService.markAsReadAndReturn(id, currentUser.getId());
        return notification.getTargetUrl() != null && !notification.getTargetUrl().isBlank()
                ? "redirect:" + notification.getTargetUrl()
                : "redirect:/notifications";
    }

    @PostMapping("/notifications/mark-all-read")
    public String markAllRead(@SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser == null) {
            return "redirect:/login";
        }

        notificationService.markAllAsRead(currentUser.getId());
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/delete-all")
    public String deleteAll(@SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser == null) {
            return "redirect:/login";
        }

        notificationService.deleteAll(currentUser.getId());
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/delete-selected")
    public String deleteSelected(
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "notificationIds", required = false) List<Long> notificationIds,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        notificationService.deleteSelected(currentUser.getId(), notificationIds != null ? notificationIds : new ArrayList<>());
        redirectAttributes.addFlashAttribute("notificationSuccess", "Đã xóa các thông báo đã chọn.");
        return "redirect:/notifications";
    }
}