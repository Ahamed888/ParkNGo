# ParkNGo - Technical Architecture

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Web Browser │  │   Customer   │  │    Staff     │       │
│  │  (HTML/CSS)  │  │   Dashboard  │  │   Dashboard  │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
                          ↓ HTTP/HTTPS
┌─────────────────────────────────────────────────────────────┐
│             PRESENTATION LAYER (Thymeleaf)                   │
│  • Login/Register Pages                                       │
│  • Dashboard Templates                                        │
│  • Form Pages (Reservation, Vehicle, etc.)                   │
│  • Error Pages                                                │
└─────────────────────────────────────────────────────────────┘
                          ↓ Request/Response
┌─────────────────────────────────────────────────────────────┐
│        WEB LAYER (Spring MVC Feature Controllers)            │
│  ┌──────────────────────┐  ┌─────────────────────────────┐  │
│  │ VehicleController    │  │ ParkingSpaceController      │  │
│  │ ReservationController│  │ PaymentController           │  │
│  │ FacilityController   │  │ PackageDiscountController   │  │
│  │ FeedbackNotification │  │ UserController & AuthController│ │
│  │ Controller           │  │                              │  │
│  │ DashboardController  │  │ Feature controllers           │  │
│  └──────────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
            ↓ Service Layer Calls
┌─────────────────────────────────────────────────────────────┐
│      BUSINESS LOGIC LAYER (Spring Services)                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ • UserService           • VehicleService            │   │
│  │ • ReservationService    • PaymentService            │   │
│  │ • DiscountService       • NotificationService       │   │
│  │ • FeedbackService       • PackageService            │   │
│  │ • ParkingFacilityService • ParkingSpaceService      │   │
│  │ • FacilityServiceService • CheckoutService          │   │
│  │ • SubscriptionService • LoyaltyService               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
            ↓ Database Query/Persistence
┌─────────────────────────────────────────────────────────────┐
│    DATA ACCESS LAYER (Spring Data JPA Repositories)          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Repository Interfaces                         │  │
│  │  • UserRepository         • VehicleRepository         │  │
│  │  • RoleRepository         • ParkingSpaceRepository    │  │
│  │  • ReservationRepository  • PaymentRepository        │  │
│  │  • DiscountRepository     • PackageRepository        │  │
│  │  • ParkingFacilityRepo    • FeedbackRepository       │  │
│  │  • NotificationRepository • FacilityServiceRepository │  │
│  │  • ReservationServiceRepository                      │  │
│  └──────────────────────────────────────────────────────┘  │
│            ↓ Hibernate ORM Mapping
│  ┌──────────────────────────────────────────────────────┐  │
│  │          Entity Classes                               │  │
│  │  • User           • Role          • Vehicle           │  │
│  │  • ParkingSpace   • Reservation   • Payment           │  │
│  │  • Package        • Discount      • Feedback          │  │
│  │  • Notification   • FacilityService                   │  │
│  │ • ParkingFacility • ReservationServiceItem (Junction)│  │
│  │ • Subscription • CheckoutQuote • PasswordResetToken  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
            ↓ SQL Queries
┌─────────────────────────────────────────────────────────────┐
│       PERSISTENCE LAYER (Microsoft SQL Server Database)       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Database: parkngo                                      │  │
│  │  Schema: Idempotent SQL migrations and DataInitializer  │  │
│  │  Records: Demo data initialized on startup            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Request Flow Example: Create Reservation

```
1. USER ACTION (Browser)
   ↓
   Customer fills out reservation form
   
2. HTTP REQUEST
   ↓
   POST /customer/reservation/create
   
3. WEB LAYER (ReservationController)
   ↓
   @PostMapping("/reservation/create")
   public String createReservation(@ModelAttribute Reservation reservation) {
   
4. SERVICE LAYER (ReservationService)
   ↓
   a) Validate vehicle exists
      → UserRepository.findById()
      → VehicleRepository.findById()
   
   b) Check availability
      → ReservationRepository.findConflictingReservations()
      → ParkingSpaceRepository.findByIdForUpdate()
      → If overlap exists → throw Exception
   
   c) Calculate price
      → hourlyRate × hours + serviceAmount - discountAmount
   
   d) Create reservation
      → Set status to PENDING
      → Save to database
   
   e) Send notification
      → NotificationService.sendReservationNotification()
      → Create Notification entity
      → Save to database
   
5. REPOSITORY LAYER (Spring Data JPA)
   ↓
   a) ReservationRepository.save(reservation)
      → Hibernate generates INSERT SQL
      
   b) NotificationRepository.save(notification)
      → Hibernate generates INSERT SQL
   
6. DATABASE LAYER (Microsoft SQL Server)
   ↓
   a) INSERT INTO reservation (...) VALUES (...)
   
   b) INSERT INTO notification (...) VALUES (...)
   
7. RESPONSE
   ↓
   HTTP 302 Redirect
   
8. PRESENTATION LAYER (Thymeleaf)
   ↓
   Redirect to /customer/reservation/{id}
   and render the reservation detail page
   
9. BROWSER DISPLAY
   ↓
   User sees confirmation and new reservation
```

## Reservation Availability Check Logic

```
Repository query (simplified JPQL semantics):
SELECT r FROM Reservation r
WHERE r.parkingSpace.id = :spaceId
  AND r.status IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE',
                   'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE')
  AND r.arrivalTime < :departureTime
  AND r.departureTime > :arrivalTime

Time Overlap Logic:
Existing:     |-----|          (arrival1=10, departure1=14)
New Request:          |-----|  (arrival2=12, departure2=16)
                   ^-- Overlaps, CONFLICT!

New Request:  |---|              (arrival2=8, departure2=10)
Existing:          |-----|       (arrival1=10, departure1=14)
              ^-- No overlap, OK!

Server-Side Validation:
✓ Checks happen in Service layer (not controller)
✓ The selected parking space is loaded with a pessimistic write lock
✓ The service applies the 15-minute buffer before querying conflicts
✓ Repository queries include all live reservation statuses
✓ Returns clear error message to user
```

## Security Architecture

```
┌─────────────────────────────────────────────────────┐
│         AUTHENTICATION & AUTHORIZATION               │
├─────────────────────────────────────────────────────┤
│                                                      │
│  USER REGISTRATION                                   │
│  ↓                                                   │
│  Input: email, password, fullname, phone            │
│  ↓                                                   │
│  Validation: Email format, password strength        │
│  ↓                                                   │
│  BCryptPasswordEncoder.encode(password)             │
│  ↓ (Never stored in plain text)                     │
│  User entity saved to database                      │
│                                                      │
│  USER LOGIN                                          │
│  ↓                                                   │
│  Input: email, password                             │
│  ↓                                                   │
│  CustomUserDetailsService.loadUserByUsername()      │
│  ↓                                                   │
│  UserRepository.findByEmail(email)                  │
│  ↓                                                   │
│  BCryptPasswordEncoder.matches(input, stored)       │
│  ↓                                                   │
│  Spring Security creates Authentication token       │
│  ↓                                                   │
│  Session created with user principal                │
│                                                      │
│  AUTHORIZATION (Role-Based Access Control)          │
│  ↓                                                   │
│  SecurityFilterChain:                                │
│  • /customer/** → requestMatchers.hasRole('CUSTOMER')│
│  • /staff/**    → requestMatchers.hasRole('STAFF')   │
│  • /admin/**    → requestMatchers.hasRole('ADMIN')   │
│  ↓                                                   │
│  SecurityContextHolder.getContext() retrieves       │
│  current Authentication                             │
│  ↓                                                   │
│  Request granted or denied based on role            │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## Database Schema (Simplified)

```
USER (1) ←──── (M) RESERVATION (M) ───→ (1) PARKING_SPACE
  │                    │                        │
  │ (1)         (1)    │                        │ (M)
  └─ VEHICLE          │                    PARKING_FACILITY
       (M)            │
                      ├─ (1:1) PAYMENT
                      │
                      └─ (M:M) FACILITY_SERVICE
                            via
                      RESERVATION_SERVICE

PAYMENT: transaction_id, amount, status, date
NOTIFICATION: recipient_id, title, message, type, read
FEEDBACK: rating (1-5), comment, response, status
DISCOUNT: code, value, type (%, $), valid_from, valid_to
PACKAGE: name, price, discount%, validity_days
```

## Key Business Rules

```
1. RESERVATION CREATION
   ├─ Customer must have at least one vehicle
   ├─ Departure time > Arrival time
   ├─ Space must not be in MAINTENANCE status
   ├─ No overlapping reservations allowed
   ├─ Price calculated: (hourlyRate × duration) + services - discount
   └─ Initial status: PENDING

2. RESERVATION STATUS FLOW
   └─ PENDING → CONFIRMED → VERIFIED → ACTIVE → COMPLETED
        ↓ (user cancels)
      CANCELLED
        ↓ (expires)
      EXPIRED

3. OVERSTAY HANDLING
   ├─ On completion, check: actual_departure > reservation_departure
   ├─ If yes: overstay_hours = HOURS between
   ├─ Charge: overstay_hours × hourlyRate
   └─ Add to total_amount

4. AVAILABILITY CHECK
   ├─ Query for conflicts in CONFIRMED, VERIFIED, ACTIVE
   ├─ Overlap logic: arrival < other_departure AND departure > other_arrival
   ├─ If any found: DENY reservation
   └─ If none: ALLOW reservation

5. DISCOUNT APPLICATION
   ├─ Discount must be ACTIVE
   ├─ Current date must be within valid date range
   ├─ If type=PERCENTAGE: discount = amount × discountValue / 100
   └─ If type=FIXED_AMOUNT: discount = discountValue (max to amount)

6. PAYMENT
   ├─ Transaction ID: TXN-{UUID}
   ├─ Must match reservation amount
   ├─ Status: PENDING → PAID (or FAILED)
   └─ Notification sent on completion
```

## Performance Considerations

```
1. QUERIES OPTIMIZED FOR:
   ├─ findConflictingReservations()
   │  └─ Checks all live booking statuses, including pending holds
   │  └─ Filters by parking space, status, and arrival time
   │
   ├─ findByCustomerId()
   │  └─ Index on customer_id for fast lookup
   │
   └─ findByFacilityId()
     └─ Index on facility_id

2. N+1 QUERY PREVENTION:
   ├─ Use @Fetch strategy on relationships
   ├─ Consider JOIN FETCH for complex queries
   └─ Lazy loading configured for optional relationships

3. CACHING OPPORTUNITIES:
   ├─ Package list (rarely changes)
   ├─ Discount codes (for validation)
   └─ Facility information

4. TRANSACTION AND DATA CONSISTENCY:
   ├─ Transaction management: Proper boundaries
   └─ Database indexes support common queries
```

## Testing Strategy

```
UNIT TESTS (Service Layer):
├─ ReservationServiceTest
│  ├─ testCreateReservation() → Success path
│  ├─ testAvailabilityCheck() → Positive case
│  ├─ testPreventDoubleBooking() → Negative case
│  └─ testReservationCancellation() → State transition
│
├─ VehicleServiceTest
│  ├─ testRegisterVehicle() → Create
│  ├─ testDuplicateRegistrationPrevention() → Validation
│  ├─ testGetCustomerVehicles() → Read
│  └─ testDeleteVehicle() → Delete
│
└─ DiscountServiceTest
   ├─ testCreateDiscount() → Create
   ├─ testDuplicateCodePrevention() → Validation
   ├─ testPercentageDiscountCalculation() → Logic
   ├─ testGetDiscountByCode() → Read
   └─ testIsValidDiscount() → Validation

INTEGRATION / CONTEXT TESTS:
├─ Spring application-context and web-security checks
├─ Schema migration and initializer checks
└─ Database-backed tests require a configured SQL Server instance

MANUAL TESTING:
├─ User registration and login
├─ Reservation creation workflow
├─ Payment processing
└─ Role-based access control
```

---

This architecture documents the security, transactional boundaries, and maintainable layered structure of the ParkNGo system.
