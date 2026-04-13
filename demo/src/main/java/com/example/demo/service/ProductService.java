package com.example.demo.service;

import com.example.demo.entity.BannerEntity;
import com.example.demo.entity.CategoryEntity;
import com.example.demo.entity.ProductEntity;
import com.example.demo.repository.BannerRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BannerRepository bannerRepository;
    private final CloudflareR2StorageService cloudflareR2StorageService;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          BannerRepository bannerRepository,
                          CloudflareR2StorageService cloudflareR2StorageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.bannerRepository = bannerRepository;
        this.cloudflareR2StorageService = cloudflareR2StorageService;
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getBestSellers() {
        return productRepository.findTop8ByActiveTrueOrderBySoldCountDesc();
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getFeaturedProducts() {
        return productRepository.findTop8ByActiveTrueAndFeaturedTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getFlashSaleProducts() {
        return productRepository.findByIsFlashSaleTrueAndActiveTrue()
                .stream()
                .filter(ProductEntity::isFlashSaleActive)
                .limit(15)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getNewestProducts() {
        return productRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))).getContent();
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getFlashSaleProducts(Pageable pageable) {
        List<ProductEntity> flashSaleProducts = productRepository.findByIsFlashSaleTrueAndActiveTrue()
            .stream()
            .filter(ProductEntity::isFlashSaleActive)
            .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), flashSaleProducts.size());
        List<ProductEntity> pageContent = start >= flashSaleProducts.size() ? List.of() : flashSaleProducts.subList(start, end);
        return new PageImpl<>(pageContent, pageable, flashSaleProducts.size());
    }

    @Transactional(readOnly = true)
    public List<ProductEntity> getProductsMarkedFlashSale() {
        return productRepository.findByIsFlashSaleTrue();
    }

    @Transactional
    public int resetExpiredFlashSaleProducts() {
        LocalDateTime now = LocalDateTime.now();
        List<ProductEntity> expiredFlashSaleProducts = productRepository.findByIsFlashSaleTrue()
                .stream()
            .filter(product -> isFlashSaleExpired(product, now))
                .toList();
        return resetFlashSaleProducts(expiredFlashSaleProducts);
    }

    @Transactional
    public int resetAllFlashSaleProducts() {
        return resetFlashSaleProducts(productRepository.findByIsFlashSaleTrue());
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryIdOrderByCreatedAtDesc(categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getProductsByCategoryName(String categoryName, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryNameIgnoreCaseOrderByCreatedAtDesc(categoryName, pageable);
    }

    @Transactional(readOnly = true)
    public ProductEntity findById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new IllegalStateException("Sản phẩm không tồn tại."));
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> searchProducts(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return productRepository.findByActiveTrueOrderBySoldCountDesc(pageable);
        }
        return productRepository.searchByKeyword(keyword.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductEntity> findAdminProducts(String keyword, String status, Long categoryId, Boolean flashSale, Pageable pageable) {
        Specification<ProductEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.join("category", jakarta.persistence.criteria.JoinType.LEFT).get("name")), like(normalizedKeyword))
            ));
        }

        if (status != null && !status.isBlank()) {
            if ("active".equalsIgnoreCase(status)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active")));
            } else if ("inactive".equalsIgnoreCase(status)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("active")));
            }
        }

        if (categoryId != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.join("category", jakarta.persistence.criteria.JoinType.LEFT).get("id"), categoryId));
        }

        if (flashSale != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("isFlashSale"), flashSale));
        }

        return productRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public CategoryEntity getCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new IllegalStateException("Danh mục không tồn tại."));
    }

    @Transactional(readOnly = true)
    public Page<CategoryEntity> findAdminCategories(String keyword, String status, Pageable pageable) {
        Specification<CategoryEntity> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), like(normalizedKeyword)),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("icon")), like(normalizedKeyword))
            ));
        }

        if (status != null && !status.isBlank()) {
            if ("active".equalsIgnoreCase(status)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active")));
            } else if ("inactive".equalsIgnoreCase(status)) {
                specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("active")));
            }
        }

        return categoryRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public ProductEntity getProduct(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<CategoryEntity> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<CategoryEntity> getAllCategories() {
        return categoryRepository.findAll();
    }

    public ProductEntity saveProduct(ProductEntity product, MultipartFile imageFile) {
        ProductEntity persisted = null;
        if (product.getId() != null) {
            persisted = productRepository.findById(product.getId()).orElse(null);
        }

        boolean categoryChanged = persisted != null
                && ((persisted.getCategory() == null && product.getCategory() != null)
                || (persisted.getCategory() != null && product.getCategory() != null && !persisted.getCategory().getId().equals(product.getCategory().getId()))
                || (persisted.getCategory() != null && product.getCategory() == null));

        if (product.getProductCode() == null || product.getProductCode().isBlank() || categoryChanged) {
            product.setProductCode(generateProductCode(product));
        }

        product = productRepository.save(product);

        if (imageFile != null && !imageFile.isEmpty()) {
            String path = cloudflareR2StorageService.uploadImage(imageFile, "products", String.valueOf(product.getId()));
            product.setImagePath(path);
            product = productRepository.save(product);
        }
        return product;
    }

    public void deleteProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Sản phẩm không tồn tại."));
        productRepository.delete(product);
    }

    public void toggleProductActive(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Sản phẩm không tồn tại."));
        product.setActive(!product.isActive());
        productRepository.save(product);
    }

    public CategoryEntity saveCategory(CategoryEntity category, MultipartFile iconFile) {
        if (iconFile != null && !iconFile.isEmpty()) {
            category.setIcon(cloudflareR2StorageService.uploadImage(
                    iconFile,
                    "categories",
                    "category-" + UUID.randomUUID().toString().substring(0, 8)
            ));
        }
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public boolean hasProductsInCategory(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        return productRepository.existsByCategoryId(categoryId);
    }

    public void backfillMissingProductCodes() {
        List<ProductEntity> products = productRepository.findAll();
        for (ProductEntity product : products) {
            if (product.getProductCode() == null || product.getProductCode().isBlank()) {
                product.setProductCode(generateProductCode(product));
                productRepository.saveAndFlush(product);
            }
        }
    }

    @Transactional(readOnly = true)
    public long countActiveProducts() {
        return productRepository.countByActiveTrue();
    }

    // ── Banner management ──
    @Transactional(readOnly = true)
    public List<BannerEntity> getActiveBanners() {
        return bannerRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<BannerEntity> getAllBanners() {
        return bannerRepository.findAllByOrderByDisplayOrderAsc();
    }

    public BannerEntity saveBanner(BannerEntity banner, MultipartFile imageFile) {
        if (imageFile != null && !imageFile.isEmpty()) {
            banner.setImagePath(cloudflareR2StorageService.uploadImage(
                    imageFile,
                    "sales",
                    "banner-" + UUID.randomUUID().toString().substring(0, 8)
            ));
        }
        return bannerRepository.save(banner);
    }

    public String saveSiteLogo(MultipartFile file) {
        return cloudflareR2StorageService.uploadImage(
                file,
                "site",
                "logo-" + UUID.randomUUID().toString().substring(0, 8)
        );
    }

    public void deleteBanner(Long bannerId) {
        bannerRepository.deleteById(bannerId);
    }

    @Transactional
    public void reorderBanners(java.util.List<Long> bannerIds) {
        for (int i = 0; i < bannerIds.size(); i++) {
            Long id = bannerIds.get(i);
            BannerEntity banner = bannerRepository.findById(id).orElse(null);
            if (banner != null) {
                banner.setDisplayOrder(i + 1);
                bannerRepository.save(banner);
            }
        }
    }

    private String generateProductCode(ProductEntity product) {
        String categoryPrefix = normalizeCategoryPrefix(product.getCategory() != null ? product.getCategory().getName() : null);
        String categoryCode = product.getCategory() != null && product.getCategory().getId() != null
                ? String.format("%04d", product.getCategory().getId())
                : "0000";
        String categorySegment = categoryPrefix + "-" + categoryCode;

        int nextSequence = 1;
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            List<ProductEntity> categoryProducts = productRepository.findByCategoryIdOrderByIdAsc(product.getCategory().getId());
            nextSequence = categoryProducts.stream()
                    .map(ProductEntity::getProductCode)
                    .filter(code -> code != null && code.startsWith(categorySegment + "-"))
                    .mapToInt(this::extractProductSequence)
                    .max()
                    .orElse(0) + 1;
        }

        return categorySegment + "-" + String.format("%04d", nextSequence);
    }

    private int extractProductSequence(String productCode) {
        try {
            String[] parts = productCode.split("-");
            return Integer.parseInt(parts[2]);
        } catch (Exception ex) {
            return 0;
        }
    }

    private String normalizeCategoryPrefix(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return "SP";
        }

        String normalized = Normalizer.normalize(categoryName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9\\s]", " ");

        String prefix = java.util.Arrays.stream(normalized.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());

        return prefix.isBlank() ? "SP" : prefix;
    }

    private String like(String value) {
        return "%" + value + "%";
    }

    private boolean isFlashSaleExpired(ProductEntity product, LocalDateTime now) {
        if (product == null || !product.isFlashSale()) {
            return false;
        }

        boolean reachedEndTime = product.getFlashSaleEnd() != null && !now.isBefore(product.getFlashSaleEnd());
        boolean soldOut = product.getFlashSaleLimit() > 0 && product.getFlashSaleSold() >= product.getFlashSaleLimit();
        return reachedEndTime || soldOut;
    }

    private int resetFlashSaleProducts(List<ProductEntity> products) {
        List<ProductEntity> productsToReset = products.stream()
                .filter(ProductEntity::isFlashSale)
                .toList();

        if (productsToReset.isEmpty()) {
            return 0;
        }

        for (ProductEntity product : productsToReset) {
            product.setFlashSale(false);
            product.setFlashSalePrice(null);
            product.setFlashSaleStart(null);
            product.setFlashSaleEnd(null);
            product.setFlashSaleLimit(0);
            product.setFlashSaleSold(0);
        }

        productRepository.saveAllAndFlush(productsToReset);
        return productsToReset.size();
    }
}
