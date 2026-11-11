# SRI LANKA INSTITUTE OF INFORMATION TECHNOLOGY
## Faculty of Computing | Department of Software Engineering

---

# FINAL PROJECT REPORT

## SE2030 - Software Engineering
### Academic Year 2026 | Year 2 Semester 1

---

# ParkNGo: Automated Smart Parking Reservation and Facility Management System

---

### Group Information

| Member Full Name | Assigned Core Module |
| :--- | :--- |
| **Nivetha** | Facility & Multi-Floor Parking Space Management |
| **Rasheed A. A. A.** | Authentication, User Security & Multi-Role Access Control |
| **Brunthapan** | Reservation Engine & Interactive Dynamic Slot Map |
| **Seneshi** | Payment Processing, Billing & Checkout Settlement |
| **Faleel M. H.** | Membership Packages, Subscriptions & Loyalty System |
| **Ashwini J.** | Feedback Moderation, KPI Analytics & Audit Logging |

---

**Degree Programme:** Bachelor of Science (Hons) in Information Technology  
**Specialization:** Software Engineering  
**Submission Date:** October 2026  
**Repository Access:** [https://github.com/ParkNGo/ParkNGo](https://github.com/ParkNGo/ParkNGo)  
**Supervisor / Module Coordinator:** Department of Software Engineering, SLIIT  

---

\newpage

# Table of Contents

1. [Executive Summary](#executive-summary)
2. [Section 1: Introduction](#section-1-introduction)
   - 1.1 Project Overview & Background
   - 1.2 Problem Statement
   - 1.3 Project Objectives
   - 1.4 Target Users & Stakeholder Analysis
   - 1.5 Project Scope & Operational Boundaries
3. [Section 2: Requirements Analysis](#section-2-requirements-analysis)
   - 2.1 Functional Requirements by Module
   - 2.2 Non-Functional Requirements (FURPS+ Model)
   - 2.3 System Limitations, Constraints & Assumptions
4. [Section 3: System Design & Architecture](#section-3-system-design--architecture)
   - 3.1 Architectural Style & High-Level System Structure
   - 3.2 Use Case Modeling & Behavioral Specifications
   - 3.3 Database Design & Relational Schema (ER Model)
   - 3.4 Software Design Pattern Implementations
   - 3.5 User Interface Wireframes & Interaction Design
5. [Section 4: Implementation Details](#section-4-implementation-details)
   - 4.1 Technology Stack & Development Environment
   - 4.2 Core Feature Implementation & Module Breakdown
   - 4.3 Validation Framework & Defensive Programming
   - 4.4 Automated Testing & Verification Suite
6. [Section 5: Project Management & Agile Scrum Methodology](#section-5-project-management--agile-scrum-methodology)
   - 5.1 Agile Scrum Framework & Team Workflow
   - 5.2 Sprint Summaries & Milestone Delivery
   - 5.3 Work Breakdown & Task Distribution Matrix
   - 5.4 Milestone Progression Timeline (Weeks 3 to 14)
7. [Section 6: Conclusion & Future Work](#section-6-conclusion--future-work)
   - 6.1 Summary of Key Achievements
   - 6.2 Technical Challenges & Engineering Solutions
   - 6.3 Future Enhancements & Scalability Roadmap
8. [Section 7: Individual Contributions, Teamwork & Lessons Learned](#section-7-individual-contributions-teamwork--lessons-learned)
   - 7.1 Individual Contribution Matrix & Reflections
   - 7.2 Team Dynamics, Communication & Conflict Resolution
9. [Section 8: References (IEEE Style)](#section-8-references-ieee-style)
10. [Section 9: Appendices](#section-9-appendices)
    - Appendix A: Test Suite Execution Report
    - Appendix B: Design Pattern Class Mapping Directory
    - Appendix C: Deployment & Environment Configuration

---

\newpage

# Executive Summary

Urban vehicle density and metropolitan commercialization have exacerbated vehicular congestion, fuel wastage, and environmental pollution resulting from uncoordinated parking searches. The **ParkNGo Automated Smart Parking Reservation and Facility Management System** is an enterprise-grade, full-stack web application designed and developed to modernize parking facility operations. Developed using **Java 17**, **Spring Boot 3.3.5**, **Thymeleaf**, **Tailwind CSS**, and **Microsoft SQL Server 2022**, ParkNGo provides an automated ecosystem connecting vehicle owners, facility staff, and administrative management.

The platform delivers comprehensive functionality across six core functional domains: role-based security with fine-grained access control, multi-floor parking facility and space configuration with dynamic allocation strategies, conflict-free reservation scheduling with interactive visual slot maps, secure multi-channel payments and billing checkout quotes, recurring subscription packages with customer loyalty programs, and real-time operational KPI analytics with event-driven audit logging.

By adopting Agile Scrum practices over four development sprints, the engineering team applied established software engineering design patterns--including **Observer**, **Singleton**, and **Strategy** patterns--to decouple business workflows, isolate failure domains, and guarantee system extensibility. Rigorous automated verification was established, yielding **171 automated test cases** across **26 test classes** executing with a **100% pass rate**. This report presents the technical specifications, architectural designs, implementation methodologies, testing verifications, and project management artifacts governing the delivery of the ParkNGo system.

---

\newpage

# Section 1: Introduction

## 1.1 Project Overview & Background
In modern metropolitan centers, finding parking spaces during peak operational hours remains an acute operational bottleneck. Studies indicate that cruising for parking accounts for over 30% of downtown traffic congestion, leading to unproductive time expenditure, elevated greenhouse gas emissions, and economic frustration. Traditional parking infrastructure relies heavily on manual physical tickets, untracked drive-in arrivals, uncoordinated paper billing, and static occupancy boards. 

ParkNGo addresses these systematic inefficiencies through an automated, centralized web platform. The system coordinates real-time parking spot inventories across multiple facilities, permits drivers to reserve bays in advance with turnover buffer guarantees, provides contact-free online payment and subscription passes, and empowers facility operators with dynamic capacity tracking, automated space assignment, and instant gate verification.

## 1.2 Problem Statement
Existing municipal and commercial parking setups suffer from:
1. **Unpredictable Bay Availability:** Motorists drive blindly to facilities without foreknowledge of whether bays matching their vehicle dimensions, electric charging (EV) needs, or accessibility requirements are free.
2. **Double-Booking and Bay Contention:** Without automated temporal conflict validation, concurrent physical drive-ins and remote bookings can contest the same physical space.
3. **Inefficient Operational Administration:** Facility supervisors lack consolidated tooling to monitor multi-floor bay occupancy, take maintenance bays offline, track revenue receipts, and handle customer service escalations.
4. **Rigid Tariff and Payment Structures:** Fragmented payment processing causes queues at entry and exit barriers, while customers lack incentives such as membership discounts, loyalty rewards, or flexible checkout quotes.

## 1.3 Project Objectives
The primary objective of the ParkNGo system is to engineer a secure, highly scalable, and user-centric parking management solution. Specific quantitative and qualitative goals include:
- **Zero-Conflict Reservation Scheduling:** Implement strict date-time interval overlap queries with mandatory 15-minute turnover buffers to eliminate double bookings.
- **Dynamic Space Allocation:** Utilize algorithmic strategy patterns to assign optimal bays according to vehicle profile criteria (EV chargers, accessibility permits, VIP passes, nearest entrance proximity).
- **Multi-Role Security Architecture:** Establish robust role-based access control (RBAC) across three distinct user roles (Customer, Staff, Administrator) with BCrypt password encryption and CSRF defense.
- **Financial Flexibility & Auditing:** Enable card payments (conforming to Luhn validation), pay-at-gate options, promotional discount codes, subscription passes, and complete refund capabilities.
- **Resilient Architectural Decoupling:** Leverage behavioral design patterns (Observer, Singleton) to guarantee that non-critical tasks (notifications, audit logging) never impede core transactional flows.
- **Enterprise Test Quality:** Maintain continuous unit, integration, and UI testing pipelines achieving a 100% build pass rate across all operational modules.

## 1.4 Target Users & Stakeholder Analysis
The system serves three primary user personas and multiple indirect stakeholders:

```
+---------------------------------------------------------------------------------------+
|                                    STAKEHOLDER MATRIX                                 |
+-------------------+-----------------------------------+-------------------------------+
| User Persona      | Key Responsibilities & Workflows  | Core Value Derived            |
+-------------------+-----------------------------------+-------------------------------+
| Registered        | - Search facilities by city/name  | Guaranteed parking bay upon   |
| Customer          | - Book slots with turnover buffer | arrival, fast digital payment,|
| (Driver)          | - Purchase subscription passes    | discounts, and transparent    |
|                   | - Manage registered vehicles      | booking management.           |
+-------------------+-----------------------------------+-------------------------------+
| Parking Facility  | - Verify reservation codes/plates | Rapid gate turnaround,        |
| Staff Operator    | - Process physical check-in/out   | real-time occupancy tracking, |
|                   | - Manage walk-in arrivals         | and clear floor-by-floor slot |
|                   | - Log on-site cash payments       | operational status.           |
+-------------------+-----------------------------------+-------------------------------+
| System            | - Configure facilities and bays   | Centralized revenue oversight,|
| Administrator     | - Manage membership packages      | automated user audit trails,  |
|                   | - Create discount promotion codes | dynamic pricing control, and  |
|                   | - Moderate customer feedback      | facility deactivation safety. |
+-------------------+-----------------------------------+-------------------------------+
| Facility Owners / | - Strategic business monitoring   | Maximized asset utilization,  |
| Investors         | - Revenue trend analysis          | reduced labor overhead, and   |
|                   | - Service demand evaluation       | high customer retention.      |
+-------------------+-----------------------------------+-------------------------------+
```

## 1.5 Project Scope & Operational Boundaries
### In-Scope Functional Modules
- Multi-facility configuration, multi-floor bay generation, and dynamic capacity tracking.
- Algorithmic space allocation implementing the Strategy Design Pattern.
- Complete customer reservation life cycle: creation, availability checking, duration extensions, cancellations, and refunds.
- Interactive SVG/HTML5 visual slot maps rendering live bay occupancy states.
- Multi-tier membership packages, gate PIN issuance, and customer loyalty rewards.
- Full payment quote preview, promotional coupon engine, and card checkout.
- Role-based Spring Security 6 authentication, staff facility assignment, and audit event dispatching.

### Out-of-Scope (Future Roadmaps)
- Physical automated barrier gate microcontrollers (hardware IoT integration).
- Real-time Optical Character Recognition (ANPR) cameras mounted at physical booms.
- Direct external bank API gateways (production Stripe/PayPal merchant keys; simulated card settlement is implemented for sandbox safety).

---

\newpage

# Section 2: Requirements Analysis

## 2.1 Functional Requirements by Module

### Module 1: Facility & Multi-Floor Space Management (Assigned: Nivetha)
- **FR1.1 (Facility CRUD):** The Administrator must be able to create, view, edit, activate, deactivate, and delete parking facilities with validated operating hours, capacity bounds, and contact details.
- **FR1.2 (Multi-Floor Bay Generation):** The system must automatically generate structured parking bays across multiple floors (1 to 50) and zones (Zone A, B, C, VIP) with standardized bay nomenclature (e.g., `A-101`, `B-205`).
- **FR1.3 (Dynamic Capacity Tracking):** The system must dynamically recalculate real-time occupied vs. available bay capacity upon check-in, check-out, and cancellation events.
- **FR1.4 (Operational Bay Toggling):** Staff and Admins must be able to toggle individual bays between `AVAILABLE`, `RESERVED`, `OCCUPIED`, and `MAINTENANCE`.
- **FR1.5 (Dynamic Space Allocation):** The system must assign optimal bays based on vehicle criteria (EV charging, accessibility permit, VIP status, nearest entrance).
- **FR1.6 (Facility Deactivation Safety):** The system must forbid deactivating or deleting a facility if active reservations or parked vehicles currently reside within it.

### Module 2: Authentication, Security & Staff Scoping (Assigned: Rasheed A. A. A.)
- **FR2.1 (User Registration & Authentication):** Customers must be able to register unique accounts with encrypted passwords (BCrypt).
- **FR2.2 (Role-Based Access Control):** The system must enforce strict role boundaries (`ROLE_CUSTOMER`, `ROLE_STAFF`, `ROLE_ADMIN`) restricting sensitive administrative and staff endpoints.
- **FR2.3 (Staff Facility Scoping):** Staff members must be strictly bounded to their assigned physical facility; attempts to verify reservations or alter bays in unassigned facilities must be denied.
- **FR2.4 (Session & CSRF Management):** All state-altering POST requests must validate CSRF tokens; session termination must cleanly invalidate cookies and direct users to role-specific destinations.

### Module 3: Reservation Engine & Dynamic Slot Map (Assigned: Brunthapan)
- **FR3.1 (Slot Availability Checking):** Customers must query available bays for specific arrival and departure windows with overlap detection.
- **FR3.2 (Turnover Buffer Enforcement):** The system must automatically enforce a mandatory 15-minute maintenance and turnover buffer between consecutive bookings on the same bay.
- **FR3.3 (Double-Booking Prevention):** The system must reject simultaneous bookings of the same vehicle across multiple bays during overlapping times.
- **FR3.4 (Interactive Visual Slot Map):** The system must render floor-by-floor graphical slot maps showing real-time color-coded states (Green = Available, Red = Occupied, Amber = Reserved, Gray = Maintenance).
- **FR3.5 (Reservation Modification & Cancellation):** Customers must be able to modify duration (charging extensions or refunding reductions) or cancel upcoming reservations.

### Module 4: Payment, Billing & Checkout Settlement (Assigned: Seneshi)
- **FR4.1 (Checkout Quote Generation):** The system must generate immutable, time-bounded checkout quotes factoring base hourly tariffs, add-on services, package discounts, and loyalty deductions.
- **FR4.2 (Payment Method Support):** Customers must be able to select online credit/debit card payment or pay-at-gate settlement.
- **FR4.3 (Card Format & Luhn Verification):** Online payments must validate cardholder name, expiry date, 3/4-digit CVC, and Luhn checksum validation.
- **FR4.4 (Refund Processing):** Cancellations or booking reductions must trigger automatic refund transactions updating the financial ledger.
- **FR4.5 (Event Publication):** Payment completions must publish asynchronous events through the Observer pattern to trigger receipts.

### Module 5: Membership Packages, Subscriptions & Loyalty (Assigned: Faleel M. H.)
- **FR5.1 (Package Creation & Management):** Admins must configure membership packages with price, duration, and facility access limitations.
- **FR5.2 (Card-Only Pass Purchase):** Customers must purchase passes exclusively using valid cards; unbacked gate cash subscriptions are disallowed.
- **FR5.3 (Digital Gate PIN Generation):** Active passes must generate a unique, cryptographically secure 6-digit gate access PIN.
- **FR5.4 (Automated Loyalty Tiering):** The system must track completed reservations and automatically promote customers from Standard (0% discount) to Gold (10% discount) upon reaching 10 completed bookings.
- **FR5.5 (Promotional Coupon Codes):** The system must validate and redeem fixed or percentage discount codes enforcing expiration dates and usage quotas.

### Module 6: Feedback Moderation, KPI Analytics & Audit Logging (Assigned: Ashwini J.)
- **FR6.1 (Customer Feedback & Ratings):** Customers must be able to submit 1-to-5 star ratings and reviews for completed reservations.
- **FR6.2 (Feedback Moderation & Response):** Administrators must be able to post official management responses to customer feedback; once responded to, customers cannot alter reviews.
- **FR6.3 (Administrative KPI Dashboard):** The system must compute real-time operational metrics: total revenue, active occupancy rate, pending check-ins, and bay utilization.
- **FR6.4 (Event-Driven Audit Logging):** Every reservation, payment, and facility change must be recorded into immutable audit logs without blocking core user operations.

---

## 2.2 Non-Functional Requirements (FURPS+ Model)

```
+---------------------------------------------------------------------------------------------------+
|                                 NON-FUNCTIONAL REQUIREMENTS SPECIFICATION                         |
+-------------------+-------------------------------------------------------------------------------+
| Dimension         | Detailed Specification & Quality Target                                       |
+-------------------+-------------------------------------------------------------------------------+
| Functionality     | Full feature set encompassing multi-tenant facility configuration, automated  |
|                   | space matching, concurrent slot booking, and financial transaction settlement.|
+-------------------+-------------------------------------------------------------------------------+
| Usability         | Responsive design rendering seamlessly on mobile, tablet, and desktop view-   |
|                   | ports using Tailwind CSS; visual color-coding adhering to WCAG 2.1 accessi-   |
|                   | bility standards; intuitive booking flow completing in under 4 steps.         |
+-------------------+-------------------------------------------------------------------------------+
| Reliability       | 99.9% uptime target; zero data loss during concurrent transactions using      |
|                   | Spring `@Transactional` ACID boundaries; graceful degradation when optional   |
|                   | services (notifications, audit logging) experience operational exceptions.    |
+-------------------+-------------------------------------------------------------------------------+
| Performance       | Average page load latency under 800ms; slot availability query latency under   |
|                   | 150ms for facilities with 500+ bays; sub-second checkout quote generation.     |
+-------------------+-------------------------------------------------------------------------------+
| Supportability    | Highly modular code structure organized by feature packages; comprehensive    |
|                   | automated test suite (171 tests); thorough API and architecture documentation.|
+-------------------+-------------------------------------------------------------------------------+
| Security (+)      | BCrypt password hashing (work factor 10); Spring Security 6 session fixation  |
|                   | protection; Synchronizer CSRF tokens on all state-changing endpoints; strict   |
|                   | Jakarta input sanitization defending against SQL Injection and XSS attacks.   |
+-------------------+-------------------------------------------------------------------------------+
```

## 2.3 System Limitations, Constraints & Assumptions
1. **Target Runtime Environment:** The application requires a Java 17+ Virtual Machine (JVM) and connects to a Microsoft SQL Server 2022 instance operating in standard dialect mode.
2. **Client-Side Assumptions:** Users must access the system via modern web browsers (Google Chrome 100+, Mozilla Firefox 100+, Apple Safari 15+) with JavaScript and CSS enabled.
3. **Turnover Buffer Constraint:** Consecutive bookings on the same parking bay require a non-negotiable 15-minute gap to prevent vehicle collision and allow turnaround.
4. **Card Gateway Emulation:** Financial card processing utilizes an integrated Luhn-verified transaction simulator adhering to PCI-DSS card structure without incurring commercial merchant charges during university evaluation.

---

\newpage

# Section 3: System Design & Architecture

## 3.1 Architectural Style & High-Level System Structure
ParkNGo is engineered upon a **Three-Tier Layered Architecture** leveraging the **Model-View-Controller (MVC)** design pattern:

```
+-----------------------------------------------------------------------------------+
|                          PRESENTATION LAYER (CLIENT-SIDE)                         |
|  Responsive HTML5, Thymeleaf Engine, Tailwind CSS, FontAwesome 6, Chart.js, JS    |
+-----------------------------------------------------------------------------------+
                                         |  HTTP / HTTPS (RESTful & MVC Routes)
                                         v
+-----------------------------------------------------------------------------------+
|                           APPLICATION CONTROLLER LAYER                            |
|  FacilityController, ReservationController, SpaceController, PaymentController... |
|  - Request Mapping, Session Validation, Form Binding, Model Construction          |
+-----------------------------------------------------------------------------------+
                                         |  Java Service Invocations
                                         v
+-----------------------------------------------------------------------------------+
|                           BUSINESS SERVICE LOGIC LAYER                            |
|  ParkingFacilityService, ReservationService, CheckoutService, LoyaltyService...   |
|  - Strategy Pattern Context (Space Allocation: EV, Accessible, VIP, Nearest)       |
|  - Observer Pattern Subject & Observers (Notifications, Audit Logs)               |
|  - Transactional Management (@Transactional ACID boundaries)                     |
+-----------------------------------------------------------------------------------+
                                         |  Spring Data JPA / JPQL Queries
                                         v
+-----------------------------------------------------------------------------------+
|                          DATA ACCESS & PERSISTENCE LAYER                          |
|  ParkingFacilityRepository, ReservationRepository, ParkingSpaceRepository...       |
|  - Spring Data JPA, Hibernate ORM, PhysicalNamingStrategyStandardImpl             |
+-----------------------------------------------------------------------------------+
                                         |  JDBC Connection Pool (HikariCP)
                                         v
+-----------------------------------------------------------------------------------+
|                               DATABASE STORAGE LAYER                              |
|  Microsoft SQL Server 2022 (Tables, Primary/Foreign Keys, Unique Constraints)     |
+-----------------------------------------------------------------------------------+
```

---

## 3.2 Use Case Modeling & Behavioral Specifications

### Complete Use Case Diagram (Textual / Tabular Specification)

```
+-------------------+-------------------------------------------------------------------------------+
| Actor             | Associated Use Cases                                                          |
+-------------------+-------------------------------------------------------------------------------+
| Customer          | UC-01: Register & Authenticate Account                                        |
| (Motorist)        | UC-02: Manage Registered Vehicles (Add, Edit, View, Remove)                    |
|                   | UC-03: Browse Facilities & Check Dynamic Slot Map Availability                |
|                   | UC-04: Book Parking Space & Select Add-On Services (Car Wash, EV Charging)    |
|                   | UC-05: Execute Payment (Card Online Checkout / Gate Settlement)               |
|                   | UC-06: Modify or Cancel Existing Booking (Extend Stay / Refund)               |
|                   | UC-07: Purchase Membership Package & Retrieve Gate Access PIN                 |
|                   | UC-08: View Loyalty Tier & Submit Feedback / Star Ratings                     |
+-------------------+-------------------------------------------------------------------------------+
| Parking Staff     | UC-09: Authenticate to Staff Console (Facility Scoped)                       |
|                   | UC-10: Verify Reservation Code or Vehicle License Plate                       |
|                   | UC-11: Process Vehicle Check-In & Gate Settlement                             |
|                   | UC-12: Process Vehicle Check-Out & Release Space to Available                  |
|                   | UC-13: Handle Walk-In Unscheduled Parking Arrivals                            |
|                   | UC-14: Toggle Bay Status (Take Offline for Maintenance)                       |
+-------------------+-------------------------------------------------------------------------------+
| System            | UC-15: Manage Facilities (Create, Update, Deactivate, Configure Hours)        |
| Administrator     | UC-16: Configure Parking Spaces & Auto-Generate Multi-Floor Bays               |
|                   | UC-17: Manage Membership Packages & Promotional Discount Vouchers             |
|                   | UC-18: Manage User Accounts, Roles & Staff Facility Assignments               |
|                   | UC-19: Moderate Customer Reviews & Publish Management Responses               |
|                   | UC-20: View Central Operational Dashboard & Real-Time KPI Analytics           |
+-------------------+-------------------------------------------------------------------------------+
```

### Detailed Use Case: UC-04 Book Parking Space
- **Primary Actor:** Registered Customer
- **Preconditions:** Customer is authenticated; facility is in `ACTIVE` status.
- **Main Success Scenario:**
  1. Customer selects target facility, vehicle, arrival time, and departure time.
  2. System validates that arrival time is in the future and minimum duration is 30 minutes.
  3. System evaluates bay availability incorporating the mandatory 15-minute turnover buffer.
  4. System executes the Strategy Pattern to select the optimal space matching vehicle attributes.
  5. System generates checkout quote factoring hourly tariffs, add-on services, and loyalty tier.
  6. Customer confirms booking and submits card payment details.
  7. Payment passes Luhn validation; reservation status transitions to `CONFIRMED`.
  8. Observer pattern triggers customer notification and audit log entries.
- **Postconditions:** Space status transitions to `RESERVED`; reservation code is generated.

---

## 3.3 Database Design & Relational Schema (ER Model)
The ParkNGo schema is implemented on Microsoft SQL Server 2022, comprising **13 primary entities** linked via strict relational integrity constraints:

```
+-----------------------------------------------------------------------------------+
|                            DATABASE ENTITY-RELATIONSHIP SCHEMA                    |
+-----------------------------------------------------------------------------------+

     [Users] 1 ------------< [Vehicle]
        | 1                       | 1
        |                         |
        | 1                       |
        |------------< [Reservation] >------------ 1 [ParkingSpace]
        |                     | 1                         | *
        | 1                   |                           |
        |                     v 1                         v 1
        |------------< [Payment]                  [ParkingFacility] 1 --< [FacilityService]
        |                                                 | 1
        | 1                                               |
        |------------< [Subscription] >-------------------| *
        |                     | *                         |
        |                     v 1                         |
        |              [MembershipPackage] >--------------+ *
        |
        |------------< [Feedback]
        |
        +------------< [Notification]
```

### Core Entity Specifications:
1. **Users (`Users` table):** `UserID` (PK, Identity), `Email` (Unique), `PasswordHash`, `FullName`, `PhoneNumber`, `UserType` (`CUSTOMER`, `STAFF`, `ADMIN`), `LoyaltyTier` (`STANDARD`, `GOLD`), `CompletedReservationsCount`, `AssignedFacilityID` (FK to ParkingFacility).
2. **ParkingFacility (`ParkingFacility` table):** `FacilityID` (PK, Identity), `Name` (Unique), `Address`, `ContactNumber`, `OpeningTime`, `ClosingTime`, `TotalCapacity`, `TotalFloors`, `CurrentOccupancy`, `Status` (`ACTIVE`, `INACTIVE`), `ManagedByAdminId`.
3. **ParkingSpace (`ParkingSpace` table):** `SpaceID` (PK, Identity), `FacilityID` (FK), `SpaceNumber`, `FloorNumber`, `Zone`, `AllocationType` (`STANDARD`, `EV`, `ACCESSIBLE`, `RESERVED_VIP`), `HourlyRate`, `Status` (`AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE`).
4. **Reservation (`Reservation` table):** `ReservationID` (PK, Identity), `ReservationCode` (Unique), `UserID` (FK), `SpaceID` (FK), `VehicleID` (FK), `FacilityID` (FK), `StartTime`, `EndTime`, `TotalAmount`, `Status` (`PENDING`, `CONFIRMED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`).
5. **Payment (`Payment` table):** `PaymentID` (PK, Identity), `ReservationID` (FK), `UserID` (FK), `Amount`, `PaymentMethod` (`CREDIT_CARD`, `DEBIT_CARD`, `GATE_PAYMENT`), `PaymentStatus` (`COMPLETED`, `PENDING`, `REFUNDED`), `TransactionDate`.
6. **MembershipPackage (`MembershipPackage` table):** `PackageID` (PK, Identity), `Name` (Unique), `Price`, `DurationDays`, `AllFacilities` (Bit), `Status` (`ACTIVE`, `INACTIVE`).
7. **Subscription (`Subscription` table):** `SubscriptionID` (PK, Identity), `UserID` (FK), `PackageID` (FK), `GateAccessPin`, `StartDate`, `EndDate`, `Status` (`ACTIVE`, `EXPIRED`, `CANCELLED`).
8. **Feedback (`Feedback` table):** `FeedbackID` (PK, Identity), `UserID` (FK), `ReservationID` (FK), `Rating` (1 to 5), `Comment`, `AdminResponse`, `CreatedAt`.

---

## 3.4 Software Design Pattern Implementations

To satisfy academic excellence and structural maintainability, ParkNGo implements three Gang-of-Four (GoF) design patterns:

### 1. Observer Pattern (Behavioral)
- **Problem Solved:** When a reservation or payment is confirmed, multiple decoupled actions must occur (customer notification dispatch, administrative audit trail recording) without coupling the core transactional service to notification or audit persistence engines.
- **Participants:**
  - `Subject` (`src/main/java/com/example/parkngo/pattern/observer/Subject.java`): Subscription interface declaring `addObserver`, `removeObserver`, and `notifyObservers`.
  - `Observer` (`Observer.java`): Contract declaring the `update(ParkingEvent event)` method.
  - `ParkingEventSubject` (`ParkingEventSubject.java`): Concrete subject maintaining registered observers.
  - `CustomerNotificationObserver` & `AuditLogObserver`: Concrete observers executing decoupled actions.
- **Decoupling Benefit:** If an observer throws an exception, `ParkingEventSubject` catches and logs it independently, allowing the user's booking/payment flow to proceed unblocked.

### 2. Singleton Pattern (Creational)
- **Problem Solved:** All event publishers across controllers and services must publish events to the exact same shared observer registry, while maintaining thread safety in a concurrent Spring web container.
- **Implementation:** `ParkingEventSubject` employs a private constructor, private static volatile instance, and synchronized `getInstance()` accessor, guaranteeing a single shared registry across all application threads.

### 3. Strategy Pattern (Behavioral)
- **Problem Solved:** Parking spaces must be dynamically allocated based on heterogeneous customer requirements (EV charging stations, disabled access, VIP privileges, nearest entrance) without giant conditional `if-else` blocks in the facility service.
- **Participants:**
  - `SpaceAllocationStrategy` (Interface): Declares `Optional<ParkingSpace> selectSpace(List<ParkingSpace> spaces, AllocationCriteria criteria)`.
  - Concrete Strategies:
    1. `EVPreferredStrategy`: Prioritizes bays equipped with functional EV charging ports.
    2. `AccessiblePriorityStrategy`: Filters and selects bays nearest to accessibility elevators.
    3. `VIPPriorityStrategy`: Prioritizes designated premium security bays.
    4. `NearestEntranceStrategy`: Computes distance heuristics selecting ground-floor entrance slots.
  - `SpaceAllocationContext`: Context component choosing the appropriate strategy dynamically at runtime based on the incoming booking request.

---

## 3.5 User Interface Wireframes & Interaction Design
The front-end user experience is crafted using modern UI design principles:
- **Consistent Visual Hierarchy:** Built with Tailwind CSS utility classes, utilizing modern slate and mist backgrounds with contrasting primary blue accents.
- **Real-Time Visual Slot Map:** Floor navigation tabs allow users to switch between floors. Bays are dynamically colored according to operational status (Green = Free, Amber = Reserved, Red = Parked, Striped Gray = Offline).
- **Proactive Input Guidance:** Custom JavaScript formats payment cards according to Luhn rules and automatically formats Sri Lankan telephone numbers (`07X-XXXXXXX` or `+94`).
- **Defensive Form Feedback:** Thymeleaf `#fields.hasErrors()` renders inline validation badges alerting users to invalid capacity bounds or overlapping booking windows before form submission.

---

\newpage

# Section 4: Implementation Details

## 4.1 Technology Stack & Development Environment
The ParkNGo software stack was curated to provide enterprise-grade reliability and performance:

```
+-----------------------------------------------------------------------------------+
|                                 TECHNOLOGY STACK SPECIFICATION                    |
+-------------------+-------------------------------+-------------------------------+
| Component Layer   | Technology / Library          | Version / Specification       |
+-------------------+-------------------------------+-------------------------------+
| Programming Lang  | Java Development Kit (OpenJDK)| Version 17 LTS                |
| Backend Framework | Spring Boot                   | Version 3.3.5                 |
| Security Engine   | Spring Security               | Version 6.3 (BCrypt hashing)  |
| ORM & Data Access | Hibernate / Spring Data JPA   | Spring Boot 3.3.5 Managed     |
| Template Engine   | Thymeleaf                     | Version 3.1.2                 |
| Frontend Styling  | Tailwind CSS & FontAwesome 6  | Modern Utility-First UI       |
| Database Engine   | Microsoft SQL Server 2022     | Docker Linux Container (1433) |
| Connection Pool   | HikariCP                      | Standard Spring Boot Pool     |
| Build Automation  | Apache Maven                  | Version 3.9+                  |
| Testing Suite     | JUnit 5 & Mockito             | 171 Automated Test Cases      |
+-------------------+-------------------------------+-------------------------------+
```

## 4.2 Core Feature Implementation & Module Breakdown
The project source code is organized into modular packages under `com.example.parkngo`:

1. **`facility` Package:** Houses `ParkingFacility`, `ParkingFacilityService`, and `FacilityController`. Implements multi-floor capacity mathematical clamping (`availableCapacity = max(0, totalCapacity - occupancy)`), case-insensitive unique facility naming, and safe soft-deactivation invariants.
2. **`space` Package:** Manages `ParkingSpace`, `ParkingSpaceService`, and `SlotMapService`. Implements automated space number generation (`generateNextSpaceNumber`) and coordinates with the Strategy pattern for optimal slot selection.
3. **`reservation` Package:** Implements `ReservationService` containing the central scheduling engine. Queries temporal slot overlaps:
   ```sql
   WHERE SpaceID = :spaceId 
     AND Status IN ('CONFIRMED', 'CHECKED_IN')
     AND :requestedStart < DATEADD(minute, 15, EndTime)
     AND :requestedEnd > DATEADD(minute, -15, StartTime)
   ```
4. **`payment` Package:** Coordinates `PaymentService` and `CheckoutService`. Implements quote expiration sweeps, payment transaction logging, and card checksum validation.
5. **`discount` Package:** Manages promotional vouchers and membership packages (`PackageService`, `DiscountService`), normalizing promo codes to uppercase and calculating percentage versus fixed amount deductions.
6. **`security` Package:** Configures `SecurityConfig` defining security filter chains, custom login handlers, and `StaffFacilityAccessService` ensuring staff can only inspect bays within their designated facility.

## 4.3 Validation Framework & Defensive Programming
The system enforces validation at every tier of the application:
- **Jakarta Bean Validation:** Annotations such as `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, and `@Pattern` on entities and DTOs reject invalid data entries at the controller boundary.
- **Cross-Field Chronological Validation:** Custom validators enforce that facility `closingTime` is strictly after `openingTime`, and reservation `endTime` is at least 30 minutes after `startTime`.
- **Database Constraints:** Relational foreign keys and SQL Server `UNIQUE` constraints guarantee relational integrity even under concurrent thread operations.

## 4.4 Automated Testing & Verification Suite
Quality assurance was verified through an extensive automated test suite consisting of **171 test cases** across **26 test classes**, achieving a **100% pass rate (0 failures, 0 errors)**:
- **Unit Testing (134 Tests):** Utilizes Mockito to isolate services and controllers, testing boundary conditions, duplicate rejections, and algorithmic calculations.
- **Integration Testing (37 Tests):** Executes full Spring Boot context tests (`@SpringBootTest`) against live Microsoft SQL Server database containers, validating complete transaction rollbacks and database constraints.

```
[INFO] Results:
[INFO] Tests run: 171, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

\newpage

# Section 5: Project Management & Agile Scrum Methodology

## 5.1 Agile Scrum Framework & Team Workflow
The development process was conducted adhering to the **Agile Scrum Framework**. The semester timeline was structured into **four distinct two-week Sprints** following the project proposal phase. Team members maintained transparent collaboration through:
- **Daily Stand-Up Meetings:** 15-minute syncs discussing tasks completed yesterday, blockers, and goals for today.
- **Sprint Planning Ceremonies:** Collaborative estimation of user story points, backlog decomposition, and sprint goal definition.
- **Sprint Review & Retrospectives:** Demonstrations of working software increments at the conclusion of each sprint followed by retrospective discussions on process improvements.
- **Version Control via Git & GitHub:** Feature-branch workflow with peer code reviews and continuous integration testing before merging into the central `main` and `Test` branches.

## 5.2 Sprint Summaries & Milestone Delivery

### Sprint 1 (Weeks 4 - 5): Architectural Inception & Core Foundation
- **Sprint Goal:** Establish database schema, Spring Boot project architecture, authentication, and user entity models.
- **Key Deliverables:**
  - MSSQL Docker container setup and schema migration scripts.
  - Spring Security configuration with BCrypt hashing and role separation.
  - User registration, login, and profile management screens.
- **Velocity:** 28 Story Points Completed.

### Sprint 2 (Weeks 6 - 7): Facility Configuration & Multi-Floor Bay Management
- **Sprint Goal:** Build facility CRUD consoles, multi-floor parking bay generation, and dynamic allocation strategies.
- **Key Deliverables:**
  - Admin facility management dashboard and add/edit forms.
  - Floor-by-floor space generator producing sequential zone bays.
  - Strategy Pattern implementation for dynamic space allocation.
- **Velocity:** 34 Story Points Completed.

### Sprint 3 (Weeks 8 - 9): Reservation Scheduling, Slot Map & Checkout Engine
- **Sprint Goal:** Deliver conflict-free reservation booking, visual slot maps, and checkout billing.
- **Key Deliverables:**
  - Reservation availability engine enforcing 15-minute turnover buffers.
  - Interactive SVG slot map displaying live bay occupancy.
  - Checkout quote generation and Luhn-validated card payment simulation.
  - Observer Pattern integration for instant notifications and audit logging.
- **Velocity:** 38 Story Points Completed.

### Sprint 4 (Weeks 10 - 12): Membership Packages, Loyalty, UI Polish & Testing
- **Sprint Goal:** Finalize subscription passes, loyalty rewards, UI redesign, and test automation.
- **Key Deliverables:**
  - Membership packages with gate access PIN generation.
  - Customer loyalty rewards engine promoting users to Gold tier.
  - Modern UI redesign using Tailwind CSS and FontAwesome 6.
  - Full automated test suite expansion reaching 171 passing test cases.
- **Velocity:** 32 Story Points Completed.

## 5.3 Work Breakdown & Task Distribution Matrix

```
+---------------------------------------------------------------------------------------------------+
|                                     TASK DISTRIBUTION MATRIX                                      |
+-------------------+---------------------------------------------------+---------------------------+
| Student Member    | Primary Module & Responsibilities                 | Key Delivered Classes     |
+-------------------+---------------------------------------------------+---------------------------+
| Nivetha           | - Parking Facility CRUD Management                | ParkingFacility.java      |
|                   | - Multi-floor parking bay generation              | ParkingSpace.java         |
|                   | - Floor/Zone layout navigation                    | ParkingFacilityService    |
|                   | - Dynamic Space Allocation Strategy Pattern       | SpaceAllocationContext    |
+-------------------+---------------------------------------------------+---------------------------+
| Rasheed A. A. A.  | - Spring Security 6 RBAC Configuration            | SecurityConfig.java       |
|                   | - User registration, authentication, BCrypt       | User.java, UserService    |
|                   | - Staff facility access scoping                   | StaffFacilityAccessService|
|                   | - CSRF & session logout handlers                  | LogoutSuccessHandler      |
+-------------------+---------------------------------------------------+---------------------------+
| Brunthapan        | - Reservation booking engine & overlap checks     | Reservation.java          |
|                   | - 15-minute turnover buffer enforcement           | ReservationService.java   |
|                   | - Interactive dynamic visual slot map             | SlotMapService.java       |
|                   | - Check-in and check-out workflows                | SlotMapController.java    |
+-------------------+---------------------------------------------------+---------------------------+
| Seneshi           | - Checkout quote calculation & fee engine         | CheckoutService.java      |
|                   | - Online credit/debit card settlement             | Payment.java              |
|                   | - Card Luhn algorithm validation                  | PaymentService.java       |
|                   | - Booking modification refund handling            | PackageCardValidation     |
+-------------------+---------------------------------------------------+---------------------------+
| Faleel M. H.      | - Membership packages & digital pass PINs         | MembershipPackage.java    |
|                   | - Subscription management & card-only purchases   | Subscription.java         |
|                   | - Customer loyalty point tiering (Gold rewards)   | LoyaltyService.java       |
|                   | - Promotional discount voucher engine             | DiscountService.java      |
+-------------------+---------------------------------------------------+---------------------------+
| Ashwini J.        | - Customer feedback submission & 5-star ratings   | Feedback.java             |
|                   | - Administrator feedback moderation & responses   | FeedbackService.java      |
|                   | - Central KPI metrics dashboard analytics         | DashboardController.java  |
|                   | - Observer pattern event-driven audit logging     | AuditLogObserver.java     |
+-------------------+---------------------------------------------------+---------------------------+
```

## 5.4 Milestone Progression Timeline (Weeks 3 to 14)

```
+---------------------------------------------------------------------------------------------------+
|                                  SE2030 PROJECT TIMELINE & MILESTONES                             |
+---------+-----------------------------------------------------------------------------------------+
| Week    | Milestone / Key Engineering Activities                                                  |
+---------+-----------------------------------------------------------------------------------------+
| Week 3  | Project Proposal Presentation & Proposal Report Submission (20% Evaluation)             |
| Week 4  | Sprint 1 Kickoff: GitHub repository setup, CI/CD pipeline, MSSQL Docker configuration   |
| Week 5  | Spring Security implementation, User entity modeling, BCrypt encryption                 |
| Week 6  | Sprint 2 Kickoff: Facility management entity, controller, and repository implementation |
| Week 7  | Multi-floor parking bay generation, Space Allocation Strategy Pattern integration       |
| Week 8  | Sprint 3 Kickoff: Reservation booking engine, date-time turnover buffer validation      |
| Week 9  | Interactive Slot Map implementation, Checkout quotes, Luhn card payment processing      |
| Week 10 | Progress Presentation & Design Document Submission (30% Evaluation - 75% Complete)      |
| Week 11 | Sprint 4 Kickoff: Membership packages, subscription passes, loyalty reward tiering      |
| Week 12 | Customer feedback moderation, KPI analytics dashboard, Observer pattern audit logging    |
| Week 13 | Final System Demonstration, Viva Examination & Final Report Submission (50% Evaluation) |
| Week 14 | Project Retrospective, Post-Viva Defense Archival, and Codebase Finalization            |
+---------+-----------------------------------------------------------------------------------------+
```

---

\newpage

# Section 6: Conclusion & Future Work

## 6.1 Summary of Key Achievements
The ParkNGo project represents a fully realized, production-ready smart parking reservation and facility management platform. Key engineering accomplishments include:
- **Flawless End-to-End Execution:** Seamless coordination between customer reservation scheduling, dynamic space allocation, staff gate check-in, and financial checkout quotes.
- **Decoupled Architecture:** Successful application of the Observer, Singleton, and Strategy design patterns ensuring high modularity and fault isolation.
- **Robust Quality Assurance:** Delivery of an exhaustive automated testing suite comprising **171 test cases across 26 test classes** executing with a **100% pass rate**.
- **Intuitive Modern Interface:** Clean, accessible front-end interfaces styled with Tailwind CSS offering real-time feedback and dynamic SVG slot maps.

## 6.2 Technical Challenges & Engineering Solutions
During development, the team encountered and overcame several complex software engineering challenges:
1. **Concurrent Slot Overlap Race Conditions:**
   - *Challenge:* Simultaneous booking requests could potentially claim the same bay for overlapping time windows.
   - *Solution:* Engineered strict JPQL temporal queries with 15-minute turnover buffers wrapped inside Spring `@Transactional(isolation = Isolation.REPEATABLE_READ)` boundaries.
2. **MSSQL Column Casing Discrepancies:**
   - *Challenge:* Test environments failed with invalid column errors due to mismatched Hibernate naming strategies (`RoleName` vs `role_name`).
   - *Solution:* Configured `PhysicalNamingStrategyStandardImpl` across all configuration profiles to guarantee consistent PascalCase mapping against Microsoft SQL Server.
3. **Thymeleaf Standalone Context Isolation:**
   - *Challenge:* Offline template testing failed to evaluate `#fields.hasGlobalErrors()`.
   - *Solution:* Programmatically wired `GenericWebApplicationContext` and `SpringWebMvcThymeleafRequestContext` into `MockServletContext` allowing offline template validation.
4. **Relational Database Cleanup Violations:**
   - *Challenge:* Test suites attempting `deleteAll()` on user tables violated foreign key constraints on seeded reservation tables.
   - *Solution:* Refactored test setup to utilize safe `findByEmail().orElseGet(...)` idempotency patterns.

## 6.3 Future Enhancements & Scalability Roadmap
While ParkNGo fulfills all functional objectives, future iterations can introduce:
1. **IoT Gate Microcontroller Integration:** Connecting ESP32 / Arduino barrier booms via MQTT protocols to automatically open upon customer PIN entry.
2. **ANPR License Plate Recognition:** Utilizing optical camera feeds at entry barriers to verify registered vehicles without physical stops.
3. **Dynamic Surge Pricing:** Machine learning algorithms adjusting hourly tariffs dynamically based on real-time facility demand, weather, and metropolitan events.
4. **Native Mobile Applications:** Packaging mobile clients using Flutter or React Native leveraging the existing RESTful service endpoints.

---

\newpage

# Section 7: Individual Contributions, Teamwork & Lessons Learned

## 7.1 Individual Contribution Matrix & Reflections

```
+---------------------------------------------------------------------------------------------------+
|                                 INDIVIDUAL REFLECTION MATRIX                                      |
+-------------------+-------------------------------------------------------------------------------+
| Member Details    | Detailed Personal Reflection on Role, Challenges & Key Learnings              |
+-------------------+-------------------------------------------------------------------------------+
| Nivetha           | * Role: Designed and developed the Facility and Space Management module,      |
|                   |   including multi-floor bay generation and the Strategy design pattern.       |
|                   | * Challenges: Ensuring facility deactivation safely blocked active bookings   |
|                   |   and coordinating dynamic space allocation without complex conditionals.     |
|                   | * Solution: Implemented database safety checks in ParkingFacilityService and  |
|                   |   used Strategy pattern contexts for clean criteria-based slot selection.     |
|                   | * Lessons: Learned practical application of GoF behavioral patterns, multi-  |
|                   |   floor spatial data modeling, and defensive bean validation.                 |
|                   | * Reflection: The module functions robustly with complete CRUD capability and  |
|                   |   dynamic space assignment operating seamlessly during all live test runs.    |
+-------------------+-------------------------------------------------------------------------------+
| Rasheed A. A. A.  | * Role: Led authentication, Spring Security 6 RBAC, user entity management,   |
|                   |   and staff multi-facility access scoping.                                    |
|                   | * Challenges: Restricting staff members exclusively to their assigned         |
|                   |   facility without leaking authorization boundaries across controllers.       |
|                   | * Solution: Built StaffFacilityAccessService to validate facility ownership   |
|                   |   before executing any space or verification action.                          |
|                   | * Lessons: Deepened knowledge of Spring Security filter chains, BCrypt        |
|                   |   salting, CSRF mitigation, and role-based request authorizers.               |
|                   | * Reflection: Security operates transparently; session handovers and staff    |
|                   |   authorizations functioned flawlessly across all integration test suites.    |
+-------------------+-------------------------------------------------------------------------------+
| Brunthapan        | * Role: Engineered the central Reservation scheduling engine, temporal       |
|                   |   overlap validation, turnover buffers, and interactive visual slot maps.     |
|                   | * Challenges: Preventing concurrent double-booking and rendering responsive   |
|                   |   visual floor-by-floor slot maps with live occupancy states.                 |
|                   | * Solution: Designed date-math JPQL queries with 15-minute buffers and SVG    |
|                   |   grid layouts driven by dynamic SlotMapService models.                       |
|                   | * Lessons: Mastered temporal database querying, turnover buffer management,   |
|                   |   and real-time UI state synchronization using Thymeleaf.                     |
|                   | * Reflection: The reservation workflow is exceptionally fast and completely   |
|                   |   prevents booking collisions even under stress testing.                      |
+-------------------+-------------------------------------------------------------------------------+
| Seneshi           | * Role: Developed payment processing, checkout quote generation, card Luhn    |
|                   |   verification, and refund settlement routines.                               |
|                   | * Challenges: Handling duration extension additional charges and duration     |
|                   |   reduction partial refunds accurately to two decimal places.                 |
|                   | * Solution: Structured CheckoutService with immutable quote tokens and        |
|                   |   automated refund ledgers linked to reservation modification hooks.          |
|                   | * Lessons: Gained comprehensive experience in financial transactional math,   |
|                   |   Luhn algorithm validation, and event-driven decoupled notifications.        |
|                   | * Reflection: Payment checkout is seamless and robust; receipts are published |
|                   |   immediately through the Observer pattern upon successful transaction.       |
+-------------------+-------------------------------------------------------------------------------+
| Faleel M. H.      | * Role: Implemented membership packages, subscription PIN issuance, customer |
|                   |   loyalty point tracking, and promotional discount voucher redemption.        |
|                   | * Challenges: Enforcing card-only payment for passes and automated promotion  |
|                   |   of customers to Gold tier upon 10 completed bookings.                       |
|                   | * Solution: Implemented validation rules restricting cash passes and hooked   |
|                   |   reservation completion events into LoyaltyService.                          |
|                   | * Lessons: Understood subscription lifecycle management, customer retention   |
|                   |   gamification architectures, and coupon validation techniques.               |
|                   | * Reflection: Pass holders can effortlessly access parking using digital      |
|                   |   PINs, and loyalty discounts apply automatically at checkout.                |
+-------------------+-------------------------------------------------------------------------------+
| Ashwini J.        | * Role: Engineered customer feedback moderation, 5-star rating analytics,     |
|                   |   administrative KPI reporting, and the Observer audit log handler.           |
|                   | * Challenges: Ensuring audit logging failures never impeded primary booking   |
|                   |   workflows and calculating live occupancy KPI statistics in real time.       |
|                   | * Solution: Developed AuditLogObserver with isolated exception handling and   |
|                   |   optimized database aggregation queries in DashboardController.              |
|                   | * Lessons: Learned event-driven logging, immutable audit trails, and data     |
|                   |   visualization using Chart.js on administrative dashboards.                  |
|                   | * Reflection: The KPI dashboard gives instant visibility into facility health,|
|                   |   and audit logging captures every state change reliably.                     |
+-------------------+-------------------------------------------------------------------------------+
```

## 7.2 Team Dynamics, Communication & Conflict Resolution
The team exhibited high cohesion throughout the semester:
- **Peer Code Reviews:** No pull request was merged into `main` without review by at least one other team member.
- **Collaborative Problem Solving:** Technical blockers--such as MSSQL configuration discrepancies and Thymeleaf context errors--were resolved via shared screen debugging sessions.
- **Equal Work Distribution:** The project scope was distributed evenly across six specialized modules, ensuring every student gained deep hands-on expertise and can confidently defend their contribution during viva examinations.

---

\newpage

# Section 8: References (IEEE Style)

[1] E. Gamma, R. Helm, R. Johnson, and J. Vlissides, *Design Patterns: Elements of Reusable Object-Oriented Software*. Reading, MA: Addison-Wesley, 1994.

[2] R. C. Martin, *Clean Code: A Handbook of Agile Software Craftsmanship*. Upper Saddle River, NJ: Prentice Hall, 2008.

[3] C. Walls, *Spring in Action*, 6th ed. Shelter Island, NY: Manning Publications, 2022.

[4] K. Beck *et al.*, "Manifesto for Agile Software Development," *Agile Alliance*, 2001. [Online]. Available: https://agilemanifesto.org/

[5] VMware Tanzu, "Spring Boot Reference Documentation," Spring.io, 2024. [Online]. Available: https://docs.spring.io/spring-boot/docs/current/reference/html/

[6] Microsoft Corporation, "Microsoft SQL Server Documentation," Microsoft Learn, 2024. [Online]. Available: https://learn.microsoft.com/en-us/sql/sql-server/

[7] Thymeleaf Project, "Tutorial: Using Thymeleaf (Spring 6 Integration)," Thymeleaf.org, 2024. [Online]. Available: https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html

[8] IEEE Computer Society, *IEEE Standard for Developing Software User Documentation Requirements*, IEEE Std 1063-2001 (R2007), 2007.

[9] H. Luhn, "Computer for Verifying Numbers," U.S. Patent 2 950 048, Aug. 23, 1960.

[10] World Wide Web Consortium (W3C), "Web Content Accessibility Guidelines (WCAG) 2.1," W3C Recommendation, Jun. 2018. [Online]. Available: https://www.w3.org/TR/WCAG21/

---

\newpage

# Section 9: Appendices

## Appendix A: Test Suite Execution Report
The ParkNGo automated test suite was executed using Apache Maven surefire plugin across all 26 test classes:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.parkngo.checkout.CheckoutServiceTest (13/13 PASSED)
[INFO] Running com.example.parkngo.discount.DiscountServiceMockitoTest (8/8 PASSED)
[INFO] Running com.example.parkngo.discount.DiscountServiceTest (5/5 PASSED)
[INFO] Running com.example.parkngo.discount.PackageServiceTest (14/14 PASSED)
[INFO] Running com.example.parkngo.facility.FacilityServiceServiceTest (9/9 PASSED)
[INFO] Running com.example.parkngo.facility.ParkingFacilityServiceTest (22/22 PASSED)
[INFO] Running com.example.parkngo.facility.ParkingFacilityValidationTest (8/8 PASSED)
[INFO] Running com.example.parkngo.feedback.FeedbackServiceTest (5/5 PASSED)
[INFO] Running com.example.parkngo.loyalty.LoyaltyServiceTest (6/6 PASSED)
[INFO] Running com.example.parkngo.pattern.observer.AuditLogObserverTest (1/1 PASSED)
[INFO] Running com.example.parkngo.pattern.observer.CustomerNotificationObserverTest (2/2 PASSED)
[INFO] Running com.example.parkngo.pattern.observer.ObserverRegistrarTest (1/1 PASSED)
[INFO] Running com.example.parkngo.pattern.observer.ParkingEventSubjectTest (5/5 PASSED)
[INFO] Running com.example.parkngo.payment.PackageCardValidationTest (2/2 PASSED)
[INFO] Running com.example.parkngo.reservation.ReservationServiceMockitoTest (6/6 PASSED)
[INFO] Running com.example.parkngo.reservation.ReservationServiceTest (21/21 PASSED)
[INFO] Running com.example.parkngo.security.CustomerLogoutSuccessHandlerTest (3/3 PASSED)
[INFO] Running com.example.parkngo.security.LogoutFlowWebTest (5/5 PASSED)
[INFO] Running com.example.parkngo.space.ParkingSpaceControllerTest (4/4 PASSED)
[INFO] Running com.example.parkngo.space.ParkingSpaceServiceTest (8/8 PASSED)
[INFO] Running com.example.parkngo.space.RedesignTemplateTest (1/1 PASSED)
[INFO] Running com.example.parkngo.space.SlotMapControllerTest (2/2 PASSED)
[INFO] Running com.example.parkngo.space.SlotMapServiceTest (7/7 PASSED)
[INFO] Running com.example.parkngo.staff.StaffFacilityAccessServiceTest (4/4 PASSED)
[INFO] Running com.example.parkngo.subscription.SubscriptionCardOnlyTest (1/1 PASSED)
[INFO] Running com.example.parkngo.vehicle.VehicleServiceTest (8/8 PASSED)
[INFO] 
[INFO] Results:
[INFO] Tests run: 171, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Appendix B: Design Pattern Class Mapping Directory

```
+---------------------------------------------------------------------------------------------------+
|                                  DESIGN PATTERN PARTICIPANTS DIRECTORY                            |
+-------------------+---------------------------+---------------------------------------------------+
| Pattern Name      | Pattern Participant Role  | Concrete Class in ParkNGo Codebase                |
+-------------------+---------------------------+---------------------------------------------------+
| Observer          | Subject (Interface)       | com.example.parkngo.pattern.observer.Subject       |
|                   | Observer (Interface)      | com.example.parkngo.pattern.observer.Observer      |
|                   | Concrete Subject          | ...pattern.observer.ParkingEventSubject           |
|                   | Concrete Observer 1       | ...pattern.observer.CustomerNotificationObserver  |
|                   | Concrete Observer 2       | ...pattern.observer.AuditLogObserver              |
|                   | Event Payload             | ...pattern.observer.ParkingEvent                  |
|                   | Observer Auto-Registrar   | ...pattern.observer.ObserverRegistrar             |
+-------------------+---------------------------+---------------------------------------------------+
| Singleton         | Singleton Instance Access | ...pattern.observer.ParkingEventSubject.getInstance()|
+-------------------+---------------------------+---------------------------------------------------+
| Strategy          | Strategy (Interface)      | ...pattern.strategy.SpaceAllocationStrategy       |
|                   | Concrete Strategy (EV)    | ...pattern.strategy.EVPreferredStrategy           |
|                   | Concrete Strategy (Access)| ...pattern.strategy.AccessiblePriorityStrategy    |
|                   | Concrete Strategy (VIP)   | ...pattern.strategy.VIPPriorityStrategy           |
|                   | Concrete Strategy (Near)  | ...pattern.strategy.NearestEntranceStrategy       |
|                   | Context Allocator         | ...pattern.strategy.SpaceAllocationContext        |
+-------------------+---------------------------+---------------------------------------------------+
```

---

## Appendix C: Deployment & Environment Configuration

### Database Creation Script
```sql
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'parkngo_test')
BEGIN
    CREATE DATABASE parkngo_test;
END
GO
```

### Application Profile Properties (`src/main/resources/application.properties`)
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=parkngodb;encrypt=true;trustServerCertificate=true
spring.datasource.username=${PARKNGO_DB_USERNAME:SA}
spring.datasource.password=${PARKNGO_DB_PASSWORD:python1*}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
spring.jpa.show-sql=false
```

---
*End of Final Project Report - SE2030 Software Engineering (SLIIT)*
