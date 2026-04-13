# PKL SHOP - Java Web E-commerce Project

PKL SHOP is a Spring Boot + Thymeleaf e-commerce website for kids fashion.
The project includes both user-side shopping flows and admin-side operations,
with payment automation, media storage on Cloudflare R2, and business reporting.

## Main Features

- Account and security
	- Local signup/login with BCrypt password hashing
	- Google OAuth2 login and Gmail linking
	- Account lock policies and CSRF protection with custom interceptor

- Shopping flow
	- Dynamic homepage with CMS-style settings and banner management
	- Product listing with search/filter and flash sale support
	- Cart with Ajax add/update/remove and live cart badge
	- Checkout with COD or BANK transfer

- Payment automation
	- VietQR image generation for bank transfer checkout
	- SePay webhook verification with API key protection
	- Amount validation + idempotent transaction handling
	- Automatic order status update when payment is confirmed

- Storage and media
	- Cloudflare R2 integration using AWS SDK v2 (S3-compatible)
	- Upload for products, categories, banners, site logo, and user avatars
	- Public CDN URL serving for media assets

- Admin operations
	- User management, lock/unlock, role-safe admin actions
	- Product/category/order management and flash sale controls
	- Audit log and notification center
	- Revenue analytics with monthly growth chart and detailed table
	- Excel/PDF export for statistics reports

## Tech Stack

- Java 17
- Spring Boot 3.x
- Spring Data JPA + Hibernate
- Spring Security + OAuth2 Client
- Thymeleaf + Bootstrap + Chart.js
- MySQL
- Apache POI + OpenPDF
- AWS SDK v2 S3 client (for Cloudflare R2)

## Repository Layout

- `demo/`: Main Spring Boot application
- `slide.html`: Project presentation slides
- `BAOCAOJAVANC-nhom8-merged.docx`: Project report document
- `huongdan.txt`: Quick local setup notes

## Quick Start

### 1) Prerequisites

- Java 17+
- Maven 3.9+ (or use `./mvnw`)
- MySQL running locally

### 2) Database and app config

Edit `demo/src/main/resources/application.properties` and set values as needed.
Recommended to use environment variables for secrets:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `SEPAY_WEBHOOK_API_KEY`
- `CLOUDFLARE_R2_ACCESS_KEY_ID`
- `CLOUDFLARE_R2_SECRET_ACCESS_KEY`

### 3) Run the app

```bash
cd demo
chmod +x mvnw
./mvnw spring-boot:run
```

Open:

- User site: `http://localhost:8080`
- Admin area: `http://localhost:8080/admin`

## Build and Test

```bash
cd demo
./mvnw clean test
./mvnw clean package
```

## Notable Endpoints

- Webhook: `POST /webhooks/sepay`
- Checkout (bank): `GET /checkout/bank/{orderId}`
- Payment status polling: `GET /orders/{orderId}/payment-status`
- Admin dashboard: `GET /admin`

## Notes

- The project currently uses server-side rendering (Thymeleaf), with selective
	JavaScript for interactive behavior (cart, payment polling, dashboard charts).
- Scheduled jobs handle payment timeout cleanup, flash sale expiry, and
	notification retention cleanup.
- If MySQL is stopped on Linux, start it before running the app.
