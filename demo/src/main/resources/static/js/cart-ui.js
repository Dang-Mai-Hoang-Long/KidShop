(function () {
    'use strict';

    const NOTICE_HOST_ID = 'cartNoticeHost';

    function ensureNoticeHost() {
        let host = document.getElementById(NOTICE_HOST_ID);
        if (host) {
            return host;
        }

        host = document.createElement('div');
        host.id = NOTICE_HOST_ID;
        host.className = 'cart-notice-host';
        host.setAttribute('aria-live', 'polite');
        host.setAttribute('aria-atomic', 'true');
        document.body.appendChild(host);
        return host;
    }

    function showNotice(message, isError) {
        if (!message) {
            return;
        }

        const host = ensureNoticeHost();
        const item = document.createElement('div');
        item.className = 'cart-notice' + (isError ? ' is-error' : '');
        item.textContent = message;
        host.appendChild(item);

        requestAnimationFrame(() => item.classList.add('is-visible'));

        window.setTimeout(() => {
            item.classList.remove('is-visible');
            window.setTimeout(() => item.remove(), 220);
        }, 2000);
    }

    function updateCartBadge(cartCount) {
        const cartButton = document.querySelector('.btn-cart-header');
        if (!cartButton) {
            return;
        }

        const safeCount = Number(cartCount);
        if (!Number.isFinite(safeCount) || safeCount <= 0) {
            const existingBadge = cartButton.querySelector('.cart-badge');
            if (existingBadge) {
                existingBadge.remove();
            }
            return;
        }

        let badge = cartButton.querySelector('.cart-badge');
        if (!badge) {
            badge = document.createElement('span');
            badge.className = 'cart-badge';
            cartButton.appendChild(badge);
        }

        badge.textContent = safeCount > 99 ? '99+' : String(safeCount);
        badge.classList.remove('cart-badge-bump');
        void badge.offsetWidth;
        badge.classList.add('cart-badge-bump');
    }

    function animateToCart(triggerButton) {
        const cartButton = document.querySelector('.btn-cart-header');
        if (!cartButton || !triggerButton) {
            return;
        }

        const startRect = triggerButton.getBoundingClientRect();
        const endRect = cartButton.getBoundingClientRect();

        const startX = startRect.left + (startRect.width / 2);
        const startY = startRect.top + (startRect.height / 2);
        const endX = endRect.left + (endRect.width / 2);
        const endY = endRect.top + (endRect.height / 2);

        const token = document.createElement('div');
        token.className = 'cart-fly-token';
        token.innerHTML = '<i class="bi bi-bag-heart-fill"></i>';
        token.style.left = startX + 'px';
        token.style.top = startY + 'px';
        token.style.setProperty('--fly-x', (endX - startX) + 'px');
        token.style.setProperty('--fly-y', (endY - startY) + 'px');

        document.body.appendChild(token);
        requestAnimationFrame(() => token.classList.add('is-active'));
        token.addEventListener('animationend', () => token.remove(), { once: true });

        cartButton.classList.remove('cart-shake');
        void cartButton.offsetWidth;
        cartButton.classList.add('cart-shake');
        cartButton.addEventListener('animationend', () => cartButton.classList.remove('cart-shake'), { once: true });
    }

    async function submitAddToCart(form, button) {
        if (!form || !button) {
            return;
        }

        if (form.dataset.submitting === 'true') {
            return;
        }

        form.dataset.submitting = 'true';
        button.disabled = true;
        button.classList.add('is-loading');

        try {
            const requestUrl = form.dataset.ajaxUrl || form.action;
            const response = await fetch(requestUrl, {
                method: 'POST',
                body: new FormData(form),
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            });

            if (response.redirected && response.url && response.url.includes('/login')) {
                window.location.href = response.url;
                return;
            }

            let payload = null;
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                payload = await response.json();
            }

            if (payload && payload.requiresLogin && payload.redirectUrl) {
                window.location.href = payload.redirectUrl;
                return;
            }

            if (!response.ok || !payload || payload.success !== true) {
                const fallbackMessage = payload && payload.message
                    ? payload.message
                    : 'Không thể thêm sản phẩm vào giỏ hàng. Vui lòng thử lại.';
                showNotice(fallbackMessage, true);
                return;
            }

            updateCartBadge(payload.cartCount);
            animateToCart(button);
            showNotice(payload.message || 'Đã thêm sản phẩm vào giỏ hàng!', false);
        } catch (error) {
            showNotice('Không thể kết nối tới máy chủ. Vui lòng thử lại.', true);
        } finally {
            form.dataset.submitting = 'false';
            button.disabled = false;
            button.classList.remove('is-loading');
        }
    }

    function initAddToCartForms() {
        const forms = document.querySelectorAll('form.add-to-cart-form');
        if (!forms.length) {
            return;
        }

        forms.forEach((form) => {
            form.addEventListener('submit', (event) => {
                event.preventDefault();
                const button = form.querySelector('button[type="submit"]');
                submitAddToCart(form, button);
            });
        });
    }

    document.addEventListener('DOMContentLoaded', initAddToCartForms);
})();
