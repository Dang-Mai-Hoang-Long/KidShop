package com.example.demo.service;

import com.example.demo.entity.CartItemEntity;
import com.example.demo.entity.ProductEntity;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserAccountRepository userAccountRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository, UserAccountRepository userAccountRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<CartItemEntity> getCartItems(Long userId) {
        return cartItemRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getCartCount(Long userId) {
        return cartItemRepository.countByUserId(userId);
    }

    public void addToCart(Long userId, Long productId, int quantity, String size) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Sản phẩm không tồn tại"));

        if (!product.isActive()) {
            throw new IllegalStateException("Sản phẩm không còn bán");
        }

        if (product.getStock() < quantity) {
            throw new IllegalStateException("Số lượng vượt quá tồn kho");
        }

        String resolvedSize = (size == null || size.isBlank()) ? "FREE" : size.trim();

        CartItemEntity existing = cartItemRepository
                .findByUserIdAndProductIdAndSelectedSize(userId, productId, resolvedSize)
                .orElse(null);

        if (existing != null) {
            int newQty = existing.getQuantity() + quantity;
            if (newQty > product.getStock()) {
                throw new IllegalStateException("Tổng số lượng vượt quá tồn kho");
            }
            existing.setQuantity(newQty);
            cartItemRepository.save(existing);
        } else {
            UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
            CartItemEntity item = new CartItemEntity();
            item.setUser(user);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setSelectedSize(resolvedSize);
            cartItemRepository.save(item);
        }
    }

    public void updateQuantity(Long userId, Long cartItemId, int quantity) {
        CartItemEntity item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalStateException("Mục không tồn tại"));

        if (!item.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Không có quyền thay đổi");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
        } else {
            if (quantity > item.getProduct().getStock()) {
                throw new IllegalStateException("Số lượng vượt quá tồn kho");
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
    }

    public void removeFromCart(Long userId, Long cartItemId) {
        CartItemEntity item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new IllegalStateException("Mục không tồn tại"));

        if (!item.getUser().getId().equals(userId)) {
            throw new IllegalStateException("Không có quyền xóa");
        }

        cartItemRepository.delete(item);
    }

    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCartTotal(Long userId) {
        return getCartItems(userId).stream()
                .map(CartItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
