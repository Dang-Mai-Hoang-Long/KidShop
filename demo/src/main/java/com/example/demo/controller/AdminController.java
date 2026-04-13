package com.example.demo.controller;

import com.example.demo.config.ExceptionMessageHelper;
import com.example.demo.dto.ProfileUpdateForm;
import com.example.demo.entity.BannerEntity;
import com.example.demo.entity.CategoryEntity;
import com.example.demo.entity.AuditLogEntity;
import com.example.demo.entity.OrderEntity;
import com.example.demo.entity.ProductEntity;
import com.example.demo.entity.SiteSettingsEntity;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.AdminUserService;
import com.example.demo.service.NotificationService;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import com.example.demo.service.StatisticsService;
import com.example.demo.validator.ProfileUpdateValidator;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private final AdminUserService adminUserService;
    private final ProfileUpdateValidator profileUpdateValidator;
    private final ProductService productService;
    private final OrderService orderService;
    private final com.example.demo.repository.SiteSettingsRepository siteSettingsRepository;
    private final com.example.demo.service.AuditService auditService;
    private final NotificationService notificationService;
    private final StatisticsService statisticsService;

    public AdminController(AdminUserService adminUserService,
                           ProfileUpdateValidator profileUpdateValidator,
                           ProductService productService,
                           OrderService orderService,
                           com.example.demo.repository.SiteSettingsRepository siteSettingsRepository,
                           com.example.demo.service.AuditService auditService,
                           NotificationService notificationService,
                           StatisticsService statisticsService) {
        this.adminUserService = adminUserService;
        this.profileUpdateValidator = profileUpdateValidator;
        this.productService = productService;
        this.orderService = orderService;
        this.siteSettingsRepository = siteSettingsRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.statisticsService = statisticsService;
    }

    @InitBinder("adminForm")
    public void initAdminBinder(WebDataBinder binder) {
        binder.addValidators(profileUpdateValidator);
    }

    @GetMapping("/admin")
    public String adminDashboard(
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "tab", defaultValue = "users") String tab,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "userKeyword", required = false) String userKeyword,
            @RequestParam(value = "userStatus", required = false) String userStatus,
            @RequestParam(value = "userGmailVerified", required = false) String userGmailVerified,
            @RequestParam(value = "productKeyword", required = false) String productKeyword,
            @RequestParam(value = "productStatus", required = false) String productStatus,
            @RequestParam(value = "productCategoryId", required = false) Long productCategoryId,
            @RequestParam(value = "productFlashSale", required = false) Boolean productFlashSale,
            @RequestParam(value = "categoryKeyword", required = false) String categoryKeyword,
            @RequestParam(value = "categoryStatus", required = false) String categoryStatus,
            @RequestParam(value = "orderKeyword", required = false) String orderKeyword,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "focusOrderCode", required = false) String focusOrderCode,
            @RequestParam(value = "selectedUserId", required = false) Long selectedUserId,
            @RequestParam(value = "auditKeyword", required = false) String auditKeyword,
            @RequestParam(value = "auditType", required = false) String auditType,
            @RequestParam(value = "statsGranularity", required = false) String statsGranularity,
            @RequestParam(value = "statsStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statsStartDate,
            @RequestParam(value = "statsEndDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statsEndDate,
            @RequestParam(value = "statsTopLimit", required = false) Integer statsTopLimit) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return "redirect:/profile";
        }

        model.addAttribute("activeTab", tab);

        model.addAttribute("totalUsers", adminUserService.countUsers());
        model.addAttribute("totalProducts", productService.countActiveProducts());
        model.addAttribute("totalOrders", orderService.countOrders());
        model.addAttribute("pendingOrders", orderService.countByStatus("PENDING"));
        model.addAttribute("totalRevenue", String.format("%,.0f", orderService.getDeliveredRevenue()) + "đ");
        model.addAttribute("verifiedGmailUsers", adminUserService.countGmailVerifiedUsers());

        populateDashboardModel(model, currentUser, page, size, userKeyword, userStatus, userGmailVerified, selectedUserId);

        int resolvedProductSize = Math.max(5, Math.min(size, 50));
        Page<ProductEntity> productPage = productService.findAdminProducts(productKeyword, productStatus, productCategoryId, productFlashSale, PageRequest.of(Math.max(page, 0), resolvedProductSize, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("productPage", productPage);
        model.addAttribute("productPageSize", resolvedProductSize);
        model.addAttribute("allCategories", productService.getAllCategories());
        model.addAttribute("productKeyword", productKeyword);
        model.addAttribute("productStatus", productStatus);
        model.addAttribute("productCategoryId", productCategoryId);
        model.addAttribute("productFlashSale", productFlashSale);

        Page<CategoryEntity> categoryPage = productService.findAdminCategories(categoryKeyword, categoryStatus, PageRequest.of(Math.max(page, 0), 10, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("categoryPage", categoryPage);
        model.addAttribute("categoryPageSize", 10);
        model.addAttribute("categoryKeyword", categoryKeyword);
        model.addAttribute("categoryStatus", categoryStatus);
        Map<Long, Boolean> categoryHasProducts = categoryPage.getContent().stream()
            .collect(Collectors.toMap(CategoryEntity::getId, category -> productService.hasProductsInCategory(category.getId())));
        model.addAttribute("categoryHasProducts", categoryHasProducts);

        Page<OrderEntity> orderPage = orderService.searchOrders(orderKeyword, orderStatus, PageRequest.of(Math.max(page, 0), 10, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("adminOrderPage", orderPage);
        model.addAttribute("orderPageSize", 10);
        model.addAttribute("orderKeyword", orderKeyword);
        model.addAttribute("orderStatus", orderStatus);
        model.addAttribute("focusedOrderCode", focusOrderCode);

        if ("audit".equalsIgnoreCase(tab)) {
            int resolvedAuditSize = Math.max(5, Math.min(size, 50));
            Page<AuditLogEntity> auditPage = auditService.searchLogs(auditKeyword, auditType, PageRequest.of(Math.max(page, 0), resolvedAuditSize, Sort.by(Sort.Direction.DESC, "createdAt")));
            model.addAttribute("auditLogsPage", auditPage);
            model.addAttribute("auditPageSize", resolvedAuditSize);
            model.addAttribute("auditKeyword", auditKeyword);
            model.addAttribute("auditType", auditType);
            model.addAttribute("auditLogs", auditPage.getContent());
        } else {
            model.addAttribute("auditPageSize", Math.max(5, Math.min(size, 50)));
            model.addAttribute("auditKeyword", auditKeyword);
            model.addAttribute("auditType", auditType);
        }

        if ("stats".equalsIgnoreCase(tab)) {
            populateStatisticsModel(model, statsGranularity, statsStartDate, statsEndDate, statsTopLimit);
        }

        return "admin/dashboard";
    }

    @GetMapping("/admin/stats/export/excel")
    public ResponseEntity<byte[]> exportStatisticsExcel(
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "statsGranularity", required = false) String statsGranularity,
            @RequestParam(value = "statsStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statsStartDate,
            @RequestParam(value = "statsEndDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statsEndDate,
            @RequestParam(value = "statsTopLimit", required = false) Integer statsTopLimit) {

        if (currentUser == null || !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return ResponseEntity.status(403).build();
        }

        StatisticsService.StatisticsDashboard dashboard = statisticsService.buildDashboard(
                statsGranularity,
                statsStartDate,
                statsEndDate,
                statsTopLimit
        );
        byte[] content = statisticsService.exportExcel(dashboard);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=doanh-thu-thong-ke.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/admin/stats/export/pdf")
    public ResponseEntity<byte[]> exportStatisticsPdf(
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "statsGranularity", required = false) String statsGranularity,
            @RequestParam(value = "statsStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statsStartDate,
            @RequestParam(value = "statsEndDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statsEndDate,
            @RequestParam(value = "statsTopLimit", required = false) Integer statsTopLimit) {

        if (currentUser == null || !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return ResponseEntity.status(403).build();
        }

        StatisticsService.StatisticsDashboard dashboard = statisticsService.buildDashboard(
                statsGranularity,
                statsStartDate,
                statsEndDate,
                statsTopLimit
        );
        byte[] content = statisticsService.exportPdf(dashboard);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=doanh-thu-thong-ke.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @PostMapping("/admin/users/save")
    public String saveUser(
            @Valid @ModelAttribute("adminForm") ProfileUpdateForm adminForm,
            BindingResult bindingResult,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            Model model,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "userKeyword", required = false) String userKeyword,
            @RequestParam(value = "userStatus", required = false) String userStatus,
            @RequestParam(value = "userGmailVerified", required = false) String userGmailVerified,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        if (adminForm.getId() != null && Objects.equals(adminForm.getId(), currentUser.getId())) {
            redirectAttributes.addFlashAttribute("adminError", "Bạn không thể chỉnh sửa hồ sơ của chính mình trong trang quản trị.");
            return redirectToUsers(page, size, userKeyword, userStatus, userGmailVerified);
        }

        if (bindingResult.hasErrors()) {
            populateDashboardModel(model, currentUser, page, size, userKeyword, userStatus, userGmailVerified, adminForm.getId());
            model.addAttribute("activeTab", "users");
            return "admin/dashboard";
        }

        try {
            adminUserService.updateUser(adminForm, currentUser.getId());
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("adminError", exception.getMessage());
            return redirectToUsers(page, size, userKeyword, userStatus, userGmailVerified);
        }

        redirectAttributes.addFlashAttribute("adminSuccess", "Đã cập nhật thông tin tài khoản.");
        return redirectToUsers(page, size, userKeyword, userStatus, userGmailVerified);
    }

    @PostMapping("/admin/users/toggle-enabled")
    public String toggleUserEnabled(
            @RequestParam("userId") Long userId,
            @RequestParam("enabled") boolean enabled,
            @RequestParam(value = "lockDuration", required = false) String lockDuration,
            @RequestParam(value = "lockReason", required = false) String lockReason,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "userKeyword", required = false) String userKeyword,
            @RequestParam(value = "userStatus", required = false) String userStatus,
            @RequestParam(value = "userGmailVerified", required = false) String userGmailVerified,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            adminUserService.toggleEnabled(userId, currentUser.getId(), enabled, lockDuration, lockReason);
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("adminError", exception.getMessage());
            return redirectToUsers(page, size, userKeyword, userStatus, userGmailVerified);
        }

        redirectAttributes.addFlashAttribute("adminSuccess", enabled ? "Đã mở khóa tài khoản." : "Đã khóa tài khoản theo thời gian đã chọn.");
        return redirectToUsers(page, size, userKeyword, userStatus, userGmailVerified);
    }

    @PostMapping("/admin/users/set-total-spend")
    public String setUserTotalSpend(
            @RequestParam("userId") Long userId,
            @RequestParam("targetTotalSpend") BigDecimal targetTotalSpend,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "userKeyword", required = false) String userKeyword,
            @RequestParam(value = "userStatus", required = false) String userStatus,
            @RequestParam(value = "userGmailVerified", required = false) String userGmailVerified,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            adminUserService.setTotalPurchasedAmount(userId, currentUser.getId(), targetTotalSpend);
            orderService.refreshUserRankByDeliveredOrders(userId);
            redirectAttributes.addFlashAttribute("adminSuccess", "Đã cập nhật tổng tiền đã mua của người dùng.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("adminError", exception.getMessage());
        }

        return redirectToUsers(page, size, userKeyword, userStatus, userGmailVerified);
    }

    @PostMapping("/admin/products/save")
    public String saveProduct(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam(value = "originalPrice", required = false) BigDecimal originalPrice,
            @RequestParam("stock") int stock,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "ageRange", required = false) String ageRange,
            @RequestParam(value = "sizes", required = false) String sizes,
            @RequestParam(value = "featured", defaultValue = "false") boolean featured,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            @RequestParam(value = "flashSale", defaultValue = "false") boolean flashSale,
            @RequestParam(value = "flashSalePrice", required = false) BigDecimal flashSalePrice,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            ProductEntity product = productId != null ? productService.getProduct(productId) : new ProductEntity();
            product.setName(name.trim());
            product.setDescription(description != null ? description.trim() : null);
            product.setPrice(price);
            product.setOriginalPrice(originalPrice);
            product.setStock(stock);
            product.setAgeRange(ageRange != null ? ageRange.trim() : null);
            product.setSizes(sizes != null ? sizes.trim() : null);
            product.setFeatured(featured);
            product.setActive(active);
            product.setFlashSale(flashSale);
            product.setFlashSalePrice(flashSalePrice);

            if (categoryId != null) {
                product.setCategory(productService.getAllCategories().stream()
                        .filter(c -> c.getId().equals(categoryId))
                        .findFirst()
                        .orElse(null));
            } else {
                product.setCategory(null);
            }

            productService.saveProduct(product, imageFile);
            redirectAttributes.addFlashAttribute("adminSuccess", "Đã lưu sản phẩm thành công!");
        } catch (Exception ex) {
            int resolvedProductSize = Math.max(5, Math.min(size, 50));
            Page<ProductEntity> productPage = productService.findAdminProducts(null, null, null, null, PageRequest.of(0, resolvedProductSize, Sort.by(Sort.Direction.DESC, "id")));
            model.addAttribute("activeTab", "products");
            model.addAttribute("productPage", productPage);
            model.addAttribute("productPageSize", resolvedProductSize);
            model.addAttribute("allCategories", productService.getAllCategories());
            model.addAttribute("productKeyword", null);
            model.addAttribute("productStatus", null);
            model.addAttribute("productCategoryId", null);
            model.addAttribute("productFlashSale", null);
            model.addAttribute("adminError", "Lỗi khi lưu sản phẩm: " + ExceptionMessageHelper.describe(ex));
            model.addAttribute("showAddProductModal", true);
            model.addAttribute("totalUsers", adminUserService.countUsers());
            model.addAttribute("totalProducts", productService.countActiveProducts());
            model.addAttribute("totalOrders", orderService.countOrders());
            model.addAttribute("pendingOrders", orderService.countByStatus("PENDING"));
            model.addAttribute("totalRevenue", String.format("%,.0f", orderService.getDeliveredRevenue()) + "đ");
            model.addAttribute("pageSize", 10);
            model.addAttribute("categoryPageSize", 10);
            model.addAttribute("orderPageSize", 10);
            model.addAttribute("auditPageSize", 10);
            model.addAttribute("auditKeyword", null);
            model.addAttribute("categoryPage", productService.findAdminCategories(null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))));
            model.addAttribute("adminOrderPage", orderService.searchOrders(null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))));
            model.addAttribute("userPage", adminUserService.findUsers(null, null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))));
            model.addAttribute("auditLogsPage", auditService.searchLogs(null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))));
            model.addAttribute("auditLogs", auditService.searchLogs(null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
            return "admin/dashboard";
        }

        return redirectToAdmin(0, size, "products");
    }

    @PostMapping("/admin/products/delete")
    public String deleteProduct(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            productService.deleteProduct(productId);
            redirectAttributes.addFlashAttribute("adminSuccess", "Đã xóa sản phẩm.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", ex.getMessage());
        }

        return redirectToAdmin(0, size, "products");
    }

    @PostMapping("/admin/products/toggle")
    public String toggleProduct(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            productService.toggleProductActive(productId);
            redirectAttributes.addFlashAttribute("adminSuccess", "Đã cập nhật trạng thái sản phẩm.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", ex.getMessage());
        }

        return redirectToAdmin(0, size, "products");
    }

    @PostMapping("/admin/orders/update-status")
    public String updateOrderStatus(
            @RequestParam("orderId") Long orderId,
            @RequestParam("newStatus") String newStatus,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "orderKeyword", required = false) String orderKeyword,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "focusOrderCode", required = false) String focusOrderCode,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            orderService.updateStatus(orderId, newStatus);
            OrderEntity order = orderService.getOrder(orderId);
            auditService.log(currentUser.getAccount(), "CẬP NHẬT ĐƠN HÀNG", "Đơn hàng #" + order.getOrderCode() + " | " + order.getStatusLabel());
            notificationService.notifyUserAndActor(
                    order.getUser().getId(),
                    currentUser.getId(),
                    "Đơn hàng #" + order.getOrderCode() + " - " + order.getStatusLabel(),
                    "Đơn hàng #" + order.getOrderCode() + " đã chuyển sang trạng thái " + order.getStatusLabel() + ".",
                    "/orders/" + order.getId(),
                    order.getId(),
                    "ORDER_STATUS_UPDATED");
            redirectAttributes.addFlashAttribute("adminSuccess", "Đã cập nhật trạng thái đơn hàng.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", ex.getMessage());
        }

        StringBuilder redirect = new StringBuilder("redirect:/admin?tab=orders");
        redirect.append("&page=").append(Math.max(page, 0));
        redirect.append("&size=").append(Math.max(size, 5));
        if (orderKeyword != null && !orderKeyword.isBlank()) {
            redirect.append("&orderKeyword=").append(orderKeyword.trim());
        }
        if (orderStatus != null && !orderStatus.isBlank()) {
            redirect.append("&orderStatus=").append(orderStatus.trim());
        }
        if (focusOrderCode != null && !focusOrderCode.isBlank()) {
            redirect.append("&focusOrderCode=").append(focusOrderCode.trim());
        }
        return redirect.toString();
    }

    @PostMapping("/admin/categories/save")
    public String saveCategory(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "icon", required = false) String icon,
            @RequestParam(value = "displayOrder", defaultValue = "10") int displayOrder,
            @RequestParam(value = "iconFile", required = false) MultipartFile iconFile,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            if (categoryId != null && productService.hasProductsInCategory(categoryId)) {
                throw new IllegalStateException("Danh mục này đang có sản phẩm nên không thể chỉnh sửa.");
            }

            CategoryEntity category = categoryId != null
                    ? productService.getCategory(categoryId)
                    : new CategoryEntity();

            category.setName(name.trim());
            category.setDescription(description != null ? description.trim() : null);
            category.setDisplayOrder(displayOrder);

            if (icon != null && !icon.isBlank()) {
                category.setIcon(icon.trim());
            }

            productService.saveCategory(category, iconFile);
            redirectAttributes.addFlashAttribute("adminSuccess", "Đã lưu danh mục!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", "Lỗi: " + ex.getMessage());
        }

        return redirectToAdmin(0, size, "categories");
    }

    @PostMapping("/admin/banners/save")
    public String saveBanner(
            @RequestParam(value = "bannerId", required = false) Long bannerId,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "linkUrl", required = false) String linkUrl,
            @RequestParam(value = "displayOrder", defaultValue = "0") int displayOrder,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            BannerEntity banner = bannerId != null
                    ? productService.getAllBanners().stream().filter(b -> b.getId().equals(bannerId)).findFirst().orElse(new BannerEntity())
                    : new BannerEntity();

            banner.setAltText(altText != null ? altText.trim() : null);
            banner.setLinkUrl(linkUrl != null ? linkUrl.trim() : null);
            banner.setDisplayOrder(displayOrder);
            banner.setActive(true);

            if (banner.getId() == null && (imageFile == null || imageFile.isEmpty())) {
                redirectAttributes.addFlashAttribute("adminError", "Vui lòng chọn ảnh banner.");
                return "redirect:/?editMode=true";
            }

            productService.saveBanner(banner, imageFile);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu banner thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", "Lỗi: " + ex.getMessage());
        }

        return "redirect:/?editMode=true";
    }

    @PostMapping("/admin/banners/delete")
    public String deleteBanner(
            @RequestParam("bannerId") Long bannerId,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            productService.deleteBanner(bannerId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa banner.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", ex.getMessage());
        }

        return "redirect:/?editMode=true";
    }

    @PostMapping("/admin/banners/reorder")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> reorderBanners(
            @RequestParam("bannerIds") java.util.List<Long> bannerIds,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {

        if (currentUser == null || !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return org.springframework.http.ResponseEntity.status(403).build();
        }

        try {
            productService.reorderBanners(bannerIds);
            return org.springframework.http.ResponseEntity.status(303)
                    .location(java.net.URI.create("/?editMode=true"))
                    .build();
        } catch (Exception ex) {
            return org.springframework.http.ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/admin/flash-sale/update")
    public String updateFlashSaleTime(
            @RequestParam("endTime") String endTime,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            java.time.LocalDateTime newEnd = java.time.LocalDateTime.parse(endTime);
            var flashProducts = productService.getProductsMarkedFlashSale();
            for (var p : flashProducts) {
                p.setFlashSaleEnd(newEnd);
                productService.saveProduct(p, null);
            }
            
            var settings = siteSettingsRepository.findById(1L).orElse(new com.example.demo.entity.SiteSettingsEntity());
            settings.setFlashSaleEnabled(true);
            settings.setFlashSaleEndTime(newEnd);
            siteSettingsRepository.save(settings);
            
            auditService.log(currentUser.getAccount(), "UPDATE_FLASH_SALE", "Gia hạn Flash Sale đến " + newEnd);
            
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật thời gian Flash Sale!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", "Lỗi: " + ex.getMessage());
        }

        return "redirect:/?editMode=true";
    }

    @PostMapping("/admin/flash-sale/disable")
    public String disableFlashSale(
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            var settings = siteSettingsRepository.findById(1L).orElse(new com.example.demo.entity.SiteSettingsEntity());
            settings.setFlashSaleEnabled(false);
            siteSettingsRepository.save(settings);
            int resetCount = productService.resetAllFlashSaleProducts();
            
            auditService.log(currentUser.getAccount(), "DISABLE_FLASH_SALE", "Tắt Flash Sale");
            
            redirectAttributes.addFlashAttribute("successMessage", "Đã tắt Flash Sale! Đã khôi phục " + resetCount + " sản phẩm về giá thường.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", "Lỗi: " + ex.getMessage());
        }

        return "redirect:/?editMode=true";
    }

    @PostMapping("/admin/site-settings/save")
    public String saveSiteSettings(
            @RequestParam(value = "shopName", required = false) String shopName,
            @RequestParam(value = "logoPath", required = false) String logoPath,
            @RequestParam(value = "footerText", required = false) String footerText,
            @RequestParam(value = "footerPhone", required = false) String footerPhone,
            @RequestParam(value = "footerAddress", required = false) String footerAddress,
            @RequestParam(value = "footerShippingText", required = false) String footerShippingText,
            @RequestParam(value = "heroBadgeText", required = false) String heroBadgeText,
            @RequestParam(value = "heroTitle", required = false) String heroTitle,
            @RequestParam(value = "heroDescription", required = false) String heroDescription,
            @RequestParam(value = "heroPrimaryButtonText", required = false) String heroPrimaryButtonText,
            @RequestParam(value = "heroPrimaryButtonUrl", required = false) String heroPrimaryButtonUrl,
            @RequestParam(value = "heroSecondaryButtonText", required = false) String heroSecondaryButtonText,
            @RequestParam(value = "heroSecondaryButtonUrl", required = false) String heroSecondaryButtonUrl,
            @RequestParam(value = "heroStat1Value", required = false) String heroStat1Value,
            @RequestParam(value = "heroStat1Label", required = false) String heroStat1Label,
            @RequestParam(value = "heroStat2Value", required = false) String heroStat2Value,
            @RequestParam(value = "heroStat2Label", required = false) String heroStat2Label,
            @RequestParam(value = "heroStat3Value", required = false) String heroStat3Value,
            @RequestParam(value = "heroStat3Label", required = false) String heroStat3Label,
            @RequestParam(value = "logoFile", required = false) MultipartFile logoFile,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) return "redirect:/login";
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) return "redirect:/profile";

        try {
            SiteSettingsEntity settings = siteSettingsRepository.findById(1L).orElseGet(SiteSettingsEntity::new);
            settings.setId(1L);
            settings.setShopName(shopName != null && !shopName.isBlank() ? shopName.trim() : settings.getShopName());
            if (logoFile != null && !logoFile.isEmpty()) {
                settings.setLogoPath(productService.saveSiteLogo(logoFile));
            } else if (logoPath != null && !logoPath.isBlank()) {
                settings.setLogoPath(logoPath.trim());
            }
            settings.setFooterText(footerText != null && !footerText.isBlank() ? footerText.trim() : settings.getFooterText());
            settings.setFooterPhone(footerPhone != null && !footerPhone.isBlank() ? footerPhone.trim() : settings.getFooterPhone());
            settings.setFooterAddress(footerAddress != null && !footerAddress.isBlank() ? footerAddress.trim() : settings.getFooterAddress());
            settings.setFooterShippingText(footerShippingText != null && !footerShippingText.isBlank() ? footerShippingText.trim() : settings.getFooterShippingText());
            settings.setHeroBadgeText(heroBadgeText != null && !heroBadgeText.isBlank() ? heroBadgeText.trim() : settings.getHeroBadgeText());
            settings.setHeroTitle(heroTitle != null && !heroTitle.isBlank() ? heroTitle.trim() : settings.getHeroTitle());
            settings.setHeroDescription(heroDescription != null && !heroDescription.isBlank() ? heroDescription.trim() : settings.getHeroDescription());
            settings.setHeroPrimaryButtonText(heroPrimaryButtonText != null && !heroPrimaryButtonText.isBlank() ? heroPrimaryButtonText.trim() : settings.getHeroPrimaryButtonText());
            settings.setHeroPrimaryButtonUrl(heroPrimaryButtonUrl != null && !heroPrimaryButtonUrl.isBlank() ? heroPrimaryButtonUrl.trim() : settings.getHeroPrimaryButtonUrl());
            settings.setHeroSecondaryButtonText(heroSecondaryButtonText != null && !heroSecondaryButtonText.isBlank() ? heroSecondaryButtonText.trim() : settings.getHeroSecondaryButtonText());
            settings.setHeroSecondaryButtonUrl(heroSecondaryButtonUrl != null && !heroSecondaryButtonUrl.isBlank() ? heroSecondaryButtonUrl.trim() : settings.getHeroSecondaryButtonUrl());
            settings.setHeroStat1Value(heroStat1Value != null && !heroStat1Value.isBlank() ? heroStat1Value.trim() : settings.getHeroStat1Value());
            settings.setHeroStat1Label(heroStat1Label != null && !heroStat1Label.isBlank() ? heroStat1Label.trim() : settings.getHeroStat1Label());
            settings.setHeroStat2Value(heroStat2Value != null && !heroStat2Value.isBlank() ? heroStat2Value.trim() : settings.getHeroStat2Value());
            settings.setHeroStat2Label(heroStat2Label != null && !heroStat2Label.isBlank() ? heroStat2Label.trim() : settings.getHeroStat2Label());
            settings.setHeroStat3Value(heroStat3Value != null && !heroStat3Value.isBlank() ? heroStat3Value.trim() : settings.getHeroStat3Value());
            settings.setHeroStat3Label(heroStat3Label != null && !heroStat3Label.isBlank() ? heroStat3Label.trim() : settings.getHeroStat3Label());
            siteSettingsRepository.save(settings);

            auditService.log(currentUser.getAccount(), "UPDATE_SITE_SETTINGS", "Cập nhật thông tin cửa hàng");
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật thông tin cửa hàng!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("adminError", "Lỗi: " + ex.getMessage());
        }

        return "redirect:/?editMode=true";
    }

    private void populateDashboardModel(Model model,
                                        CurrentUser currentUser,
                                        int page,
                                        int size,
                                        String userKeyword,
                                        String userStatus,
                                        String userGmailVerified,
                                        Long selectedUserId) {
        int resolvedSize = Math.max(5, Math.min(size, 50));
        Page<UserAccountEntity> userPage = adminUserService.findUsers(userKeyword, userStatus, userGmailVerified, PageRequest.of(Math.max(page, 0), resolvedSize, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("userPage", userPage);
        model.addAttribute("userKeyword", userKeyword);
        model.addAttribute("userStatus", userStatus);
        model.addAttribute("userGmailVerified", userGmailVerified);
        model.addAttribute("pageSize", resolvedSize);

        Map<Long, BigDecimal> userEffectiveSpendMap = userPage.getContent().stream()
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        orderService::getEffectiveSpendByUser
                ));
        Map<Long, String> userTotalSpentMap = userPage.getContent().stream()
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        user -> String.format("%,.0f", userEffectiveSpendMap.getOrDefault(user.getId(), BigDecimal.ZERO)) + "đ"
                ));
        Map<Long, String> userTotalSpendInputMap = userPage.getContent().stream()
                .collect(Collectors.toMap(
                        UserAccountEntity::getId,
                        user -> userEffectiveSpendMap.getOrDefault(user.getId(), BigDecimal.ZERO)
                                .setScale(0, RoundingMode.HALF_UP)
                                .toPlainString()
                ));
        model.addAttribute("userTotalSpentMap", userTotalSpentMap);
        model.addAttribute("userTotalSpendInputMap", userTotalSpendInputMap);

        Long resolvedSelectedId = resolveSelectedUserId(selectedUserId, userPage);
        UserAccountEntity selectedUser = resolvedSelectedId == null ? null : adminUserService.findUser(resolvedSelectedId);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("showDetailModal", selectedUser != null);
        if (selectedUser != null) {
            model.addAttribute("adminForm", adminUserService.toForm(selectedUser));
            model.addAttribute("isSelfSelected", Objects.equals(selectedUser.getId(), currentUser.getId()));
        }
    }

    private Long resolveSelectedUserId(Long selectedUserId, Page<UserAccountEntity> userPage) {
        if (selectedUserId != null && userPage.getContent().stream().anyMatch(user -> Objects.equals(user.getId(), selectedUserId))) {
            return selectedUserId;
        }
        return null;
    }

    private String redirectToUsers(int page, int size, String userKeyword, String userStatus, String userGmailVerified) {
        StringBuilder redirect = new StringBuilder(redirectToAdmin(page, size, "users"));
        appendQueryParam(redirect, "userKeyword", userKeyword);
        appendQueryParam(redirect, "userStatus", userStatus);
        appendQueryParam(redirect, "userGmailVerified", userGmailVerified);
        return redirect.toString();
    }

    private void populateStatisticsModel(Model model,
                                         String statsGranularity,
                                         LocalDate statsStartDate,
                                         LocalDate statsEndDate,
                                         Integer statsTopLimit) {
        StatisticsService.StatisticsDashboard dashboard = statisticsService.buildDashboard(
                statsGranularity,
                statsStartDate,
                statsEndDate,
                statsTopLimit
        );

        model.addAttribute("statsDashboard", dashboard);
        model.addAttribute("statsGranularity", dashboard.getGranularity());
        model.addAttribute("statsStartDate", dashboard.getStartDate());
        model.addAttribute("statsEndDate", dashboard.getEndDate());
        model.addAttribute("statsTopLimit", dashboard.getTopLimit());

        model.addAttribute("statsRevenueLabels", dashboard.getRevenueLabels());
        model.addAttribute("statsRevenueValues", dashboard.getRevenueValues());
        model.addAttribute("statsTopProducts", dashboard.getTopProducts());
        model.addAttribute("statsTopCategories", dashboard.getTopCategories());

        model.addAttribute("statsRangeTopLabels", dashboard.getTopProducts().stream().map(StatisticsService.TopProductStat::getName).toList());
        model.addAttribute("statsRangeTopValues", dashboard.getTopProducts().stream().map(StatisticsService.TopProductStat::getRevenue).toList());
        model.addAttribute("statsCategoryLabels", dashboard.getTopCategories().stream().map(StatisticsService.CategoryRevenueStat::getCategoryName).toList());
        model.addAttribute("statsCategoryValues", dashboard.getTopCategories().stream().map(StatisticsService.CategoryRevenueStat::getRevenue).toList());

        model.addAttribute("statsDaySummary", dashboard.getDayTopSummary());
        model.addAttribute("statsMonthSummary", dashboard.getMonthTopSummary());
        model.addAttribute("statsYearSummary", dashboard.getYearTopSummary());
        model.addAttribute("statsDayLabels", dashboard.getDayTopSummary().getChartLabels());
        model.addAttribute("statsDayValues", dashboard.getDayTopSummary().getChartValues());
        model.addAttribute("statsMonthLabels", dashboard.getMonthTopSummary().getChartLabels());
        model.addAttribute("statsMonthValues", dashboard.getMonthTopSummary().getChartValues());
        model.addAttribute("statsYearLabels", dashboard.getYearTopSummary().getChartLabels());
        model.addAttribute("statsYearValues", dashboard.getYearTopSummary().getChartValues());
        model.addAttribute("statsMonthlyGrowthLabels", dashboard.getMonthlyGrowthLabels());
        model.addAttribute("statsMonthlyRevenueValues", dashboard.getMonthlyRevenueValues());
        model.addAttribute("statsMonthlyGrowthPercentValues", dashboard.getMonthlyGrowthPercentValues());
        model.addAttribute("statsMonthlyGrowthStats", dashboard.getMonthlyGrowthStats());
        model.addAttribute("statsMonthlyGrowthSummary", dashboard.getMonthlyGrowthSummary());
    }

    private void appendQueryParam(StringBuilder redirect, String key, String value) {
        if (value != null && !value.isBlank()) {
            redirect.append("&").append(key).append("=").append(value.trim());
        }
    }

    private String redirectToAdmin(int page, int size, String tab) {
        return new StringBuilder("redirect:/admin?tab=")
                .append(tab)
                .append("&page=").append(Math.max(page, 0))
                .append("&size=").append(Math.max(size, 5))
                .toString();
    }
}