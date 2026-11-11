# 🅿️ ParkNGo — Smart Parking Reservation & Facility Management System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/Database-Microsoft%20SQL%20Server-blue.svg)](https://www.microsoft.com/en-us/sql-server)
[![Security](https://img.shields.io/badge/Security-Spring%20Security%206-red.svg)](https://spring.io/projects/spring-security)
[![UI](https://img.shields.io/badge/Frontend-Thymeleaf%20%2B%20TailwindCSS-06B6D4.svg)](https://tailwindcss.com/)

An enterprise-grade, full-stack web application designed to modernize urban parking operations. **ParkNGo** bridges vehicle drivers, on-site facility operators, and central administrators into a coordinated ecosystem featuring dynamic visual garage slot maps, buffer-guaranteed reservations, card-only membership passes, automated loyalty discounts, and role-scoped operational consoles.

---

## 🌟 Key Highlights

* **Interactive Visual Garage Floor Map:** Live SVG/CSS grid visualization of multi-floor parking bays with real-time occupancy polling, zone filtering (Standard, EV, Accessible, VIP), and keyboard navigation.
* **Collision-Free Reservation Engine:** Temporal booking validation with automated 15-minute turnover buffer guarantees to eliminate double-booking between advance reservations and physical walk-ins.
* **Membership Passes & Gate PINs:** Card-validated subscription passes with digital PIN generation for rapid gate turnaround.
* **Dynamic Pricing & Loyalty Gamification:** Customer loyalty tiers (Standard & Gold), automatic booking volume upgrades, and promotional coupon voucher codes (percentage/fixed discounts with expiration and quota limits).
* **Enterprise Security & Scoped Operations:** Spring Security 6 role-based access control (`ROLE_CUSTOMER`, `ROLE_STAFF`, `ROLE_ADMIN`), CSRF protection, and strict physical facility-scoping ensuring staff can only inspect and manage their assigned garage.
* **GoF Design Patterns:** Production implementation of the **Strategy Pattern** for dynamic space allocation and the **Observer Pattern** for decoupled audit logging and customer notifications.

---

## 🏗️ System Architecture

ParkNGo follows a modular, layered architecture ensuring clear separation of concerns, transactional boundaries (`@Transactional`), and maintainability:

```
┌────────────────────────────────────────────────────────┐
│             Presentation Layer (Thymeleaf UI)          │
│   Tailwind CSS  •  Responsive Viewports  •  SVG Maps   │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP / REST / Form Post
┌───────────────────────────▼────────────────────────────┐
│                  Web & Security Layer                  │
│ Spring Security 6 RBAC  •  CSRF Filters  • Controllers │
└───────────────────────────┬────────────────────────────┘
                            │ Service Method Invocations
┌───────────────────────────▼────────────────────────────┐
│                    Business Layer                      │
│ Domain Services  •  Strategy Allocators • Observers    │
└───────────────────────────┬────────────────────────────┘
                            │ Spring Data JPA / Hibernate
┌───────────────────────────▼────────────────────────────┐
│                   Persistence Layer                    │
│   Microsoft SQL Server 2022  •  Idempotent Migrations  │
└────────────────────────────────────────────────────────┘
```

### Applied Design Patterns

1. **Strategy Pattern (`com.example.parkngo.pattern.strategy`)**
   * Dynamically selects the optimal parking bay based on vehicle attributes and customer preferences.
   * Concrete strategies include: `NearestEntranceStrategy`, `EVPreferredStrategy`, `AccessiblePriorityStrategy`, and `VIPPriorityStrategy`.
2. **Observer Pattern (`com.example.parkngo.pattern.observer`)**
   * Decouples state-altering parking events from secondary actions.
   * `ParkingEventSubject` notifies registered observers:
     * `CustomerNotificationObserver`: Issues in-app notifications and triggers optional async email dispatch.
     * `AuditLogObserver`: Creates immutable audit trails for administrative monitoring without blocking customer checkouts.

---

## 🚀 Core Modules & Capabilities

### 1. Customer Experience Portal
* **Facility Discovery:** Search facilities by city, address, operating hours, and rate per hour.
* **Interactive Bay Selection:** Inspect floor plans, check bay statuses (`AVAILABLE`, `OCCUPIED`, `MAINTENANCE`), and book specific spots.
* **Online Checkout:** Secure credit/debit card processing validated with the Luhn algorithm.
* **Membership Subscriptions:** Purchase duration-based passes (e.g., 30 days) with gate PIN issuance.
* **Loyalty & Discounts:** Redeem promo codes and enjoy automatic Gold-tier discounts once qualifying bookings are achieved.
* **Vehicle Management:** Register multiple vehicles with license plate format validation.
* **Customer Feedback:** Submit ratings (1–5 stars) and reviews for completed parking stays.
* **Account Recovery:** Self-service password reset via time-limited, cryptographically hashed tokens.

### 2. Staff Operations Console
* **Facility-Scoped Access:** Staff operators are strictly bound to their assigned physical facility.
* **Real-Time Occupancy Dashboard:** Instant glance at available vs. occupied bays across all floors.
* **Gate Verification:** Fast check-in/check-out verification using reservation reference codes or license plate numbers.
* **Walk-In Allocation:** Direct on-site space assignment for physical drive-in customers.
* **Operational Bay Toggling:** Mark bays offline for maintenance or release them back into circulation.

### 3. Administrator Command Center
* **Facility Management:** Full CRUD operations for parking facilities, capacities, floors, and operating hours.
* **Space Layout Configuration:** Generate, inspect, and update space numbers, rate tiers, and allocation types.
* **Membership Packages:** Create and manage tiered subscription products with pricing, duration, and facility limitations.
* **Promotional Discount Management:** Configure percentage or fixed-amount vouchers with usage caps and date bounds.
* **Analytics & Reports:** Monitor revenue trends, occupancy analytics, and moderate customer reviews.
* **User & Staff Assignment:** Manage user accounts and assign staff members to designated facilities.

---

## 🛠️ Technology Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.3.5 |
| **Security** | Spring Security 6 (BCrypt, CSRF, RBAC) |
| **ORM / Data** | Spring Data JPA / Hibernate 6 |
| **Database** | Microsoft SQL Server 2019/2022 |
| **Template Engine** | Thymeleaf 3 with Thymeleaf Extras Spring Security 6 |
| **CSS Framework** | Tailwind CSS (v3) |
| **Testing** | JUnit 5, Mockito, AssertJ |
| **Build Tool** | Apache Maven 3.9+ |

---

## ⚡ Quick Start & Installation

### Prerequisites
* **Java Development Kit (JDK) 21** or higher
* **Apache Maven 3.8+**
* **Microsoft SQL Server** (Local instance, SQL Express, or Docker container)

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/ParkNGo.git
cd ParkNGo
```

### 2. Database Setup
Create the application database in Microsoft SQL Server:
```sql
CREATE DATABASE [parkngo];
CREATE DATABASE [parkngo_test]; -- Dedicated for integration tests
```

### 3. Configure Local Credentials
The repository uses gitignored environment property files so your credentials are never exposed:

1. **Database Credentials:** Copy the template:
   ```bash
   cp .env.local.properties.example .env.local.properties
   ```
   Set your local database password in `.env.local.properties`:
   ```properties
   spring.datasource.password=your_db_password
   ```

2. **Email Notifications (Optional):** If you wish to test Gmail SMTP notifications:
   ```bash
   cp .env.mail.properties.example .env.mail.properties
   ```
   Set your credentials:
   ```properties
   app.mail.enabled=true
   spring.mail.username=your-account@gmail.com
   spring.mail.password=your-16-character-app-password
   ```

### 4. Build and Run
```bash
# Compile and package
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run
```

The application will start on: **`http://localhost:8080`**

---

## 👥 Default Demo Credentials

For quick evaluation and testing, the application automatically seeds initial accounts when running in development:

| Role | Email | Password | Access / Purpose |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin@example.com` | `admin123` | Full access to administrator console & settings |
| **Staff** | `staff@example.com` | `staff123` | Assigned to downtown facility for gate operations |
| **Customer** | `customer@example.com` | `customer123` | Search, book spaces, purchase passes, and view bookings |

> *Note: In production environments, replace default initializers with secure credential provisioning.*

---

## 🧪 Testing & Verification

The project includes unit and integration tests covering security, quote settlement, and parking strategies:

```bash
# Run the test suite
mvn test

# Run a specific test class
mvn test -Dtest=CheckoutServiceTest
```

---

## 👥 Project Team & Modules

* **Rasheed A. A. A.** — Reservation Booking Engine & Overlap Validation
* **Nivetha R.** — Parking Facility & Space Allocation Strategy Management
* **Faleel M. H.** — Membership Packages, Subscriptions & Loyalty System
* **Brunthapan R.** — Vehicle Registration & Dynamic Slot Map
* **Jeyasegaran A.** — Parking Space Inventory & Management
* **Sehansa W. D. S.** — Payment Settlement & Customer Feedback System

---

## 📄 License & Academic Note

This project was developed as an academic software engineering project. Feel free to explore, clone, and use the concepts for educational and portfolio demonstration purposes.
