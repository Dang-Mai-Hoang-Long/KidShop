package com.example.demo.config;

import com.example.demo.entity.BannerEntity;
import com.example.demo.entity.CategoryEntity;
import com.example.demo.entity.ProductEntity;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.repository.BannerRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.ProductService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BannerRepository bannerRepository;
    private final ProductService productService;

    public DemoDataInitializer(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, CategoryRepository categoryRepository, ProductRepository productRepository, BannerRepository bannerRepository, ProductService productService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.bannerRepository = bannerRepository;
        this.productService = productService;
    }

    @Override
    public void run(String... args) {
        if (!userAccountRepository.existsByAccountIgnoreCase("admin")) {
            UserAccountEntity admin = new UserAccountEntity();
            admin.setAccount("admin");
            admin.setFirstName("Quản");
            admin.setLastName("Trị");
            admin.setEmail("admin@kidsshop.local");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setPhoneNumber("0900000000");
            admin.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
            admin.setAddress("Hệ thống");
            admin.setRole("ADMIN");
            admin.setRank("DIAMOND");
            admin.setEnabled(true);
            userAccountRepository.save(admin);
        }

        if (categoryRepository.count() == 0) {
            CategoryEntity cat1 = createCategory("Đồ sơ sinh", "Mềm, nhẹ, an toàn cho da bé", "bi-stars", 1);
            CategoryEntity cat2 = createCategory("Váy bé gái", "Màu tươi, dễ phối giày dép", "bi-balloon-heart", 2);
            CategoryEntity cat3 = createCategory("Set bé trai", "Năng động cho bé đi học, đi chơi", "bi-lightning-charge-fill", 3);
            CategoryEntity cat4 = createCategory("Phụ kiện", "Mũ, tất, kẹp tóc và túi mini", "bi-bag-heart", 4);
            CategoryEntity cat5 = createCategory("Đồ ngủ", "Pyjama mềm mại cho bé ngủ ngon", "bi-moon-stars-fill", 5);
            CategoryEntity cat6 = createCategory("Áo khoác", "Giữ ấm, chống nắng cho bé", "bi-cloud-sun-fill", 6);

            categoryRepository.save(cat1);
            categoryRepository.save(cat2);
            categoryRepository.save(cat3);
            categoryRepository.save(cat4);
            categoryRepository.save(cat5);
            categoryRepository.save(cat6);

            createProduct("Set áo quần cotton bé gái", "Chất vải thoáng, dễ giặt và nhanh khô. Phù hợp dùng hằng ngày cho bé gái từ 1-5 tuổi. Họa tiết dễ thương, nhiều màu lựa chọn.", new BigDecimal("159000"), new BigDecimal("219000"), 50, cat1, "/assets/sales/sale1.webp", "1-5 tuổi", "S,M,L,XL", true, 128);
            createProduct("Váy xòe nhẹ cho bé gái", "Phù hợp đi sinh nhật, đi chơi cuối tuần. Lên ảnh rất nổi bật và xinh xắn. Chất liệu cotton pha nhẹ, không gây kích ứng.", new BigDecimal("189000"), new BigDecimal("269000"), 35, cat2, "/assets/sales/sale2.webp", "2-7 tuổi", "S,M,L", true, 95);
            createProduct("Bộ bé trai năng động", "Đường may chắc chắn, bé vận động thoải mái cả ngày mà vẫn giữ form tốt. Thiết kế thể thao, phong cách.", new BigDecimal("169000"), new BigDecimal("229000"), 60, cat3, "/assets/sales/sale1.webp", "2-8 tuổi", "S,M,L,XL", true, 87);
            createProduct("Áo khoác mỏng chống nắng", "Che nắng nhẹ, gọn gàng khi mang theo. Tiện dùng khi đi học và dạo chơi. Chống UV nhẹ.", new BigDecimal("139000"), new BigDecimal("189000"), 40, cat6, "/assets/sales/sale2.webp", "1-6 tuổi", "S,M,L", true, 72);
            createProduct("Pyjama bé trai hình gấu", "Chất liệu cotton 100%, mềm mại và thoáng khí. Giúp bé ngủ ngon hơn mỗi đêm.", new BigDecimal("149000"), new BigDecimal("199000"), 30, cat5, "/assets/sales/sale1.webp", "1-5 tuổi", "S,M,L", false, 63);
            createProduct("Mũ bucket con thỏ", "Mũ vải mềm, có tai thỏ đáng yêu. Bảo vệ bé khỏi nắng khi đi chơi.", new BigDecimal("79000"), new BigDecimal("120000"), 100, cat4, "/assets/sales/sale2.webp", "1-6 tuổi", "FREE", false, 156);
            createProduct("Set body sơ sinh trắng", "Set 3 chiếc body liền cho bé sơ sinh. Chất cotton organic, không hóa chất.", new BigDecimal("199000"), new BigDecimal("299000"), 25, cat1, "/assets/sales/sale1.webp", "0-12 tháng", "S,M", true, 45);
            createProduct("Váy công chúa tím pastel", "Váy voan nhẹ, phù hợp tiệc sinh nhật hoặc sự kiện. Đính nơ xinh xắn.", new BigDecimal("259000"), new BigDecimal("359000"), 20, cat2, "/assets/sales/sale2.webp", "3-8 tuổi", "S,M,L", true, 38);
            createProduct("Quần short thể thao bé trai", "Chất thun co giãn 4 chiều. Phù hợp vận động, chơi thể thao.", new BigDecimal("99000"), new BigDecimal("149000"), 80, cat3, "/assets/sales/sale1.webp", "3-10 tuổi", "M,L,XL", false, 112);
            createProduct("Tất cotton hoạt hình (5 đôi)", "Bộ 5 đôi tất cotton mềm, nhiều mẫu hoạt hình dễ thương cho bé.", new BigDecimal("69000"), new BigDecimal("99000"), 200, cat4, "/assets/sales/sale2.webp", "1-8 tuổi", "S,M,L", false, 234);
            createProduct("Áo khoác gió unisex", "Áo khoác chống gió nhẹ, phù hợp cả bé trai và bé gái. Có mũ trùm.", new BigDecimal("179000"), new BigDecimal("259000"), 45, cat6, "/assets/sales/sale1.webp", "2-8 tuổi", "S,M,L,XL", true, 67);
            createProduct("Đầm hoa nhí bé gái", "Đầm cotton in hoa nhí vintage, phù hợp đi chơi, đi học, chụp ảnh.", new BigDecimal("209000"), new BigDecimal("289000"), 30, cat2, "/assets/sales/sale2.webp", "2-7 tuổi", "S,M,L", true, 54);
        }

        productService.backfillMissingProductCodes();

        // Seed flash sale products (or refresh expired ones)
        List<ProductEntity> flashProducts = productRepository.findByIsFlashSaleTrueAndActiveTrue();
        if (flashProducts.isEmpty()) {
            List<ProductEntity> allProducts = productRepository.findAll();
            int count = 0;
            for (ProductEntity p : allProducts) {
                if (count < 8) {
                    p.setFlashSale(true);
                    p.setFlashSalePrice(p.getPrice().multiply(new BigDecimal("0.7")));
                    p.setFlashSaleLimit(50);
                    p.setFlashSaleSold(count * 5);
                    p.setFlashSaleStart(LocalDateTime.now().minusHours(2));
                    p.setFlashSaleEnd(LocalDateTime.now().plusDays(3));
                    productRepository.save(p);
                    count++;
                }
            }
        } else {
            // Refresh expired flash sale end times
            for (ProductEntity p : flashProducts) {
                if (p.getFlashSaleEnd() != null && p.getFlashSaleEnd().isBefore(LocalDateTime.now())) {
                    p.setFlashSaleEnd(LocalDateTime.now().plusDays(3));
                    productRepository.save(p);
                }
            }
        }

        // Seed banners if empty
        if (bannerRepository.count() == 0) {
            BannerEntity b1 = new BannerEntity();
            b1.setImagePath("/assets/sales/sale1.webp");
            b1.setAltText("Khuyến mãi thời trang trẻ em");
            b1.setDisplayOrder(1);
            b1.setActive(true);
            bannerRepository.save(b1);

            BannerEntity b2 = new BannerEntity();
            b2.setImagePath("/assets/sales/sale2.webp");
            b2.setAltText("Sản phẩm nổi bật cho bé");
            b2.setDisplayOrder(2);
            b2.setActive(true);
            bannerRepository.save(b2);
        }
    }

    private CategoryEntity createCategory(String name, String description, String icon, int order) {
        CategoryEntity cat = new CategoryEntity();
        cat.setName(name);
        cat.setDescription(description);
        cat.setIcon(icon);
        cat.setDisplayOrder(order);
        cat.setActive(true);
        return cat;
    }

    private void createProduct(String name, String desc, BigDecimal price, BigDecimal originalPrice, int stock, CategoryEntity category, String image, String ageRange, String sizes, boolean featured, int soldCount) {
        ProductEntity p = new ProductEntity();
        p.setName(name);
        p.setDescription(desc);
        p.setPrice(price);
        p.setOriginalPrice(originalPrice);
        p.setStock(stock);
        p.setCategory(category);
        p.setImagePath(image);
        p.setAgeRange(ageRange);
        p.setSizes(sizes);
        p.setActive(true);
        p.setFeatured(featured);
        p.setSoldCount(soldCount);
        p.setCreatedAt(LocalDateTime.now());
        productService.saveProduct(p, null);
    }
}