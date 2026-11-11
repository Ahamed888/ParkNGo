# ParkNGo Test Cases & Test Suite Specification

---

## 1. Executive Summary

This document specifies the complete test suite for the **ParkNGo** automated parking management system. 

* **Total Test Classes:** 26
* **Total Test Cases:** 171
* **Execution Status:** 100% Passed (171 passed, 0 failures, 0 errors, 0 skipped)
* **Build Result:** BUILD SUCCESS

| Category | Classes | Tests | Passed | Failed | Errors |
| :--- | :---: | :---: | :---: | :---: | :---: |
| Mockito Unit Tests & Bean Validation | 22 | 134 | 134 | 0 | 0 |
| Spring Boot Live SQL Server Integration Tests (`@SpringBootTest`) | 4 | 37 | 37 | 0 | 0 |
| **Total** | **26** | **171** | **171** | **0** | **0** |

---

## 2. Test Execution & Setup Instructions

To execute the entire test suite against the live test database:

1. Ensure the SQL Server container is running:
   ```bash
   docker ps | grep sqlserver2022
   ```
2. Verify that the `parkngo_test` database exists (credentials default to user `SA` with password `python1*`):
   ```bash
   /opt/mssql-tools18/bin/sqlcmd -S localhost -U SA -P 'python1*' -C -Q "SELECT name FROM sys.databases WHERE name='parkngo_test';"
   ```
3. Run Maven tests from the project root:
   ```bash
   mvn test
   ```

---

## 3. Root Cause Analysis: Diagnostic & Resolution Summary

During test execution, specific failures were encountered and resolved as follows:

1. **Local Socket Network Isolation:**
   - *Cause:* Running Maven commands inside an isolated environment blocked TCP socket connections to `localhost:1433` (`Operation not permitted`).
   - *Fix:* Executed tests with local socket access enabled so Spring Boot can reach SQL Server on port 1433.

2. **Missing Test Catalog & Credentials:**
   - *Cause:* `application-test.properties` expected `databaseName=parkngo_test` with unexpanded environment variables `${PARKNGO_TEST_DB_USERNAME}` and `${PARKNGO_TEST_DB_PASSWORD}`.
   - *Fix:* Created database `parkngo_test` in SQL Server and added default fallbacks `${PARKNGO_TEST_DB_USERNAME:SA}` and `${PARKNGO_TEST_DB_PASSWORD:python1*}`.

3. **Hibernate Physical Naming Strategy Collision:**
   - *Cause:* `application-test.properties` lacked `PhysicalNamingStrategyStandardImpl`, causing Hibernate to name columns in snake_case (`role_name`) while raw SQL scripts in `DataInitializer.java` referenced PascalCase (`RoleName`).
   - *Fix:* Configured `spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl` in `application-test.properties`.

4. **Online Payment Notification Dispatch:**
   - *Cause:* `ReservationServiceMockitoTest` required two notifications upon online booking: `"Booking Confirmed & Paid"` and `"Payment Received"` (`NotificationType.PAYMENT_SUCCESSFUL`). `ReservationService.java` was only sending the confirmation.
   - *Fix:* Added `"Payment Received"` notification dispatch inside `ReservationService.java` for card payments.

5. **Thymeleaf Standalone Engine Web Context:**
   - *Cause:* In `RedesignTemplateTest.java`, standalone template rendering threw `IllegalStateException` on `#fields.hasGlobalErrors()` because Spring MVC's request context was absent.
   - *Fix:* Configured `GenericWebApplicationContext` and `SpringWebMvcThymeleafRequestContext` on the mock servlet environment.

6. **Foreign Key Constraint Violations on User Cleanup:**
   - *Cause:* In `VehicleServiceTest.java`, calling `userRepository.deleteAll()` violated database foreign key constraints against existing seeded reservations.
   - *Fix:* Updated test setup to safely find or create the test user (`findByEmail().orElseGet(...)`) without deleting database records.

---

## 4. Complete Test Inventory by Class and Method

### 4.1 Facility Management Module (`com.example.parkngo.facility`)

#### `ParkingFacilityServiceTest.java` (22 Tests)
- Layer: Mockito Unit Test
- Result: 22 / 22 Passed

1. `testCreateFacilitySuccess`: Creates active parking facility with valid attributes.
2. `testCreateFacilityDuplicateNameCaseInsensitive`: Rejects duplicate names ignoring case/whitespace.
3. `testGetFacilityByIdSuccess`: Fetches existing facility entity by ID.
4. `testGetFacilityByIdNotFound`: Throws exception for non-existent facility ID.
5. `testGetAllFacilities`: Queries only ACTIVE facilities for customer views.
6. `testGetAllFacilitiesIncludingInactive`: Queries all facilities regardless of status for admin views.
7. `testUpdateFacilitySuccess`: Updates name, address, hours, and description.
8. `testUpdateFacilityCapacityLessThanOccupancy`: Prevents lowering total capacity below current occupied vehicles.
9. `testDeactivateFacilitySuccess`: Successfully deactivates facility when no cars or reservations exist.
10. `testDeactivateFacilityBlockedByActiveReservations`: Prevents deactivation if confirmed bookings exist.
11. `testDeactivateFacilityBlockedByOccupiedBays`: Prevents deactivation if vehicles are parked.
12. `testDeactivateFacilityBlockedByReservedBays`: Prevents deactivation if spaces are in RESERVED status.
13. `testReactivateFacilitySuccess`: Reactivates an INACTIVE facility.
14. `testToggleFacilityStatus`: Toggles facility status between active and inactive.
15. `testHardDeleteFacilitySuccess`: Deletes facility when zero parking spaces exist.
16. `testHardDeleteFacilityBlockedWhenSpacesExist`: Blocks hard deletion when spaces are still associated.
17. `testUpdateOccupancy`: Clamps physical occupancy between 0 and total capacity.
18. `testEntityHelperMethods`: Verifies computed fields (`availableCapacity`, `occupancyPercentage`, hours).
19. `testUpdateFacilityRejectReducingFloorsBelowExistingSpaces`: Prevents lowering floor count below highest floor containing slots.
20. `testUpdateFacilitySucceedsWithValidFloors`: Increases floor count and validates `getFloorNumbers()`.
21. `testCreateFacilityValidationFailures`: Validates constraints (name, address, phone, capacity 1-5000, hours).
22. `testDeactivateFacilityBlockedByFutureReservations`: Blocks deactivating facility with future reservations.

#### `ParkingFacilityValidationTest.java` (8 Tests)
- Layer: Jakarta Bean Validation
- Result: 8 / 8 Passed

1. `testValidFacility`: Valid entity passes validation with 0 violations.
2. `testValidPhoneNumbers`: Accepts valid Sri Lankan phone number formats.
3. `testInvalidPhoneNumbers`: Rejects malformed and short phone numbers.
4. `testTotalCapacityBounds`: Enforces capacity range between 1 and 5000.
5. `testOpeningClosingTimeValidation`: Enforces closing time after opening time.
6. `testFacilityNameConstraints`: Enforces name length between 3 and 100 characters.
7. `testFacilityServiceValidation`: Enforces non-negative service price and valid decimal precision.
8. `testTotalFloorsValidation`: Enforces floor range between 1 and 50.

#### `FacilityServiceServiceTest.java` (9 Tests)
- Layer: Mockito Unit Test
- Result: 9 / 9 Passed

1. `testCreateServiceSuccess`: Creates add-on service linked to a facility.
2. `testCreateServiceDuplicateNameInSameFacility`: Blocks duplicate service name within the same facility.
3. `testGetServiceByIdSuccess`: Retrieves service by ID.
4. `testGetServiceByIdNotFound`: Throws exception when service is missing.
5. `testGetFacilityServices`: Returns all services for a facility.
6. `testGetAvailableServices`: Filters services by status AVAILABLE.
7. `testUpdateServiceSuccess`: Modifies service details and pricing.
8. `testHardDeleteService`: Permanently deletes a service.
9. `testEntityHelperMethods`: Validates price formatting and active status checks.

---

### 4.2 Parking Space & Slot Map Module (`com.example.parkngo.space`)

#### `ParkingSpaceServiceTest.java` (8 Tests)
- Layer: Mockito Unit Test
- Result: 8 / 8 Passed

1. `testCreateSpaceValidFloorSucceeds`: Allows creating space on valid floor.
2. `testCreateSpaceFloorExceedingFacilityFloorsThrows`: Rejects floor higher than facility total floors.
3. `testCreateSpaceZeroOrNegativeFloorThrows`: Rejects floor <= 0.
4. `testUpdateSpaceFloorExceedingFacilityFloorsThrows`: Rejects update with floor exceeding facility total floors.
5. `testExtractZoneCode`: Normalizes zone codes (e.g., ZONE-A, VIP-ZONE).
6. `testGenerateNextSpaceNumber_NoExistingSpaces`: Defaults initial space code to 01.
7. `testGenerateNextSpaceNumber_IncrementsSequentially`: Increments space codes sequentially.
8. `testGenerateNextSpaceNumber_Floor2ZoneB`: Formats sequential code for Floor 2 Zone B.

#### `ParkingSpaceControllerTest.java` (4 Tests)
- Layer: Spring MVC Mock
- Result: 4 / 4 Passed

1. `testViewParkingSpacesAdminIncludesStoredSpaces`: Verifies model contains stored spaces for autocomplete.
2. `testSuggestSpaceCodesReturnsMatches`: Returns matching space codes as JSON.
3. `testSuggestSpaceCodesEmptyQuery`: Returns empty array for blank query string.
4. `testSuggestSpaceCodesFacilityFilter`: Scopes autocomplete suggestions by facility.

#### `SlotMapServiceTest.java` (7 Tests)
- Layer: Mockito Unit Test
- Result: 7 / 7 Passed

1. `occupiedBayShowsCarAndScheduledRelease`: Displays parked vehicle and scheduled release time.
2. `overdueCarDoesNotBecomeAvailableEvenForLaterWindow`: Overstay vehicle remains occupied.
3. `untrackedOccupancyDoesNotInventReleaseEstimate`: Untracked walk-in car shows occupied without release date.
4. `laterBookingCanUseBayAfterKnownDepartureAndTurnover`: Slot reopens after departure plus 15-minute buffer.
5. `editExcludesOnlyItsOwnBooking`: Booking modification excludes itself from slot conflict checks.
6. `turnoverAndOperationallyUnavailableBaysAreRespected`: Maintenance bays remain unbookable.
7. `snapshotKeepsAllDatabaseFloorsAndSpacesDynamic`: Dynamically constructs slot grid per floor.

#### `SlotMapControllerTest.java` (2 Tests)
- Layer: Spring MVC Mock
- Result: 2 / 2 Passed

1. `cannotExcludeAnotherCustomersBooking`: Blocks customer from accessing another customer's booking exclusion.
2. `invalidWindowRejectedBeforeQuery`: Rejects invalid arrival/departure time window with HTTP 400.

#### `RedesignTemplateTest.java` (1 Test)
- Layer: Thymeleaf Offline Engine Test
- Result: 1 / 1 Passed

1. `redesignedPagesRenderWithPopulatedAndEmptyData`: Renders all redesigned templates offline with mock contexts.

---

### 4.3 Security & Access Control Module (`com.example.parkngo.security` & `com.example.parkngo.staff`)

#### `CustomerLogoutSuccessHandlerTest.java` (3 Tests)
- Layer: Mockito Unit Test
- Result: 3 / 3 Passed

1. `customerGoesToGoodbyePage`: Redirects CUSTOMER to `/auth/goodbye`.
2. `staffAndAdminKeepHomeRedirect`: Redirects STAFF and ADMIN to `/`.
3. `unauthenticatedLogoutKeepsHomeRedirect`: Redirects unauthenticated session to `/`.

#### `LogoutFlowWebTest.java` (5 Tests)
- Layer: `@SpringBootTest` + MockMvc
- Result: 5 / 5 Passed

1. `customerLogoutRequiresCsrfAndRedirectsToGoodbye`: Enforces CSRF token and redirects customer to goodbye.
2. `staffAndAdminLogoutStillRedirectHome`: Staff/Admin logout redirects to home.
3. `goodbyePageIsPublicOnlyAfterAuthenticationIsGone`: Confirms goodbye page is anonymously accessible.
4. `legacyRememberMeCookieDoesNotSignInOnItsOwn`: Rejects unverified remember-me cookies.
5. `existingSessionCanOpenSignInPageToSwitchAccounts`: Allows logged-in user to access login page to switch accounts.

#### `StaffFacilityAccessServiceTest.java` (4 Tests)
- Layer: Mockito Unit Test
- Result: 4 / 4 Passed

1. `assignedFacilityAccessSucceeds`: Grants access to assigned facility.
2. `alteredFacilityIdIsDenied`: Denies access when requesting an unassigned facility ID.
3. `alteredSpaceIdOutsideAssignedFacilityIsDenied`: Denies space operations belonging to outside facilities.
4. `unassignedStaffIsDenied`: Denies all operations for staff without facility assignments.

---

### 4.4 Observer Pattern Module (`com.example.parkngo.pattern.observer`)

#### `ParkingEventSubjectTest.java` (5 Tests)
- Layer: Mockito & Multithreaded Unit Test
- Result: 5 / 5 Passed

1. `getInstance_returnsSameObject`: Singleton pattern verification.
2. `getInstance_isSafeAcrossConcurrentCalls`: Thread-safe concurrent initialization.
3. `notifyObservers_deliversToRegisteredObserversButNotRemovedOne`: Dispatches events to subscribed observers only.
4. `addObserver_ignoresDuplicateAndNullObservers`: Prevents duplicate or null observer registrations.
5. `notifyObservers_continuesWhenAnObserverFails`: Fault-tolerant notification loop continues on observer error.

#### `CustomerNotificationObserverTest.java` (2 Tests)
- Layer: Mockito Unit Test
- Result: 2 / 2 Passed

1. `update_sendsOneNotificationWithTheEventValues`: Dispatches customer notification from parking event.
2. `update_swallowsNotificationServiceFailure`: Swallows persistence exceptions to keep publisher non-blocking.

#### `AuditLogObserverTest.java` (1 Test)
- Layer: Mockito Unit Test
- Result: 1 / 1 Passed

1. `update_acceptsEventsWithAndWithoutReferenceId`: Records audit log entry with or without reference IDs.

#### `ObserverRegistrarTest.java` (1 Test)
- Layer: Mockito Unit Test
- Result: 1 / 1 Passed

1. `registerObservers_registersEachOnceEvenWhenCalledTwice`: Idempotent observer registration during startup.

---

### 4.5 Payment, Checkout, Loyalty & Feedback Module

#### `CheckoutServiceTest.java` (13 Tests)
- Layer: Mockito Unit Test
- Result: 13 / 13 Passed

1. `previewCheckout_appliesConfiguredPackageDiscountAndSkipsPromo`: Package discount takes precedence.
2. `previewCheckout_packageHoldFails_fallsThroughToDiscountCode`: Falls back to promo code when package holds fail.
3. `previewCheckoutForFacility_doesNotUsePassOutsideItsComplexes`: Restricts package pass to configured facilities.
4. `previewPackagePurchase_neverHoldsAPackage_evenIfOneIsActive`: Purchasing package does not consume existing pass benefits.
5. `previewCheckout_discountAtPerCustomerCap_fallsThroughToFullPrice`: Cap limit reverts price to standard rate.
6. `commitCheckout_finalizesQuoteAndConfirmsRedemption`: Finalizes quote and updates redemption counter.
7. `commitCheckout_retriedWithSameOrder_isIdempotentNoOp`: Retrying identical order commit is an idempotent no-op.
8. `commitCheckout_reusedAgainstADifferentOrder_throwsMismatch`: Throws mismatch exception when reusing quote token.
9. `commitCheckout_expiredQuote_releasesHoldAndThrows`: Releases balance holds on expired quote.
10. `commitCheckout_unknownQuote_throwsNotFound`: Unknown quote token throws resource not found.
11. `rollbackCheckout_restoresVisitAndDiscountUse`: Rollback restores customer pass balance and discount usage.
12. `rollbackCheckout_nothingCommittedForThatOrder_isAnIdempotentNoOp`: Rollback on uncommitted order completes safely.
13. `sweepExpiredQuotes_releasesEachExpiredOpenQuote`: Scheduled job cleans up abandoned quotes.

#### `LoyaltyServiceTest.java` (6 Tests)
- Layer: Mockito Unit Test
- Result: 6 / 6 Passed

1. `getLoyaltyStatus_newCustomer_defaultsToStandardWithZeroCount`: New customer starts at STANDARD tier.
2. `onReservationCompleted_incrementsCountAndPersists`: Increments completed booking count.
3. `onReservationCompleted_unlocksGoldAtTenCompletedReservations`: 10 bookings promotes user to GOLD.
4. `onReservationCompleted_doesNotDowngradeAnAlreadyGoldCustomer`: Retains GOLD tier permanently.
5. `getLoyaltyDiscountRate_standardTier_isZero`: STANDARD tier receives 0% discount.
6. `getLoyaltyDiscountRate_goldTier_isTenPercent`: GOLD tier receives 10% discount.

#### `FeedbackServiceTest.java` (5 Tests)
- Layer: Mockito Unit Test
- Result: 5 / 5 Passed

1. `deleteCustomerFeedback_successWhenNoManagementResponse`: Customer can delete unresponded feedback.
2. `deleteCustomerFeedback_throwsExceptionWhenManagementResponseExists`: Blocks customer deletion after management response.
3. `updateCustomerFeedback_successWhenNoManagementResponse`: Customer can update unresponded feedback.
4. `updateCustomerFeedback_throwsExceptionWhenManagementResponseExists`: Blocks customer edit after management response.
5. `deleteFeedback_adminDeletionThrowsUnsupportedOperationException`: Admins cannot delete feedback entries.

#### `PackageCardValidationTest.java` (2 Tests)
- Layer: Mockito Unit Test
- Result: 2 / 2 Passed

1. `acceptsSupportedCardFormatsWithMatchingSecurityCode`: Validates Visa, Mastercard, Amex with valid CVCs.
2. `rejectsExtraDigitsWrongCvcAndExpiredMonth`: Rejects invalid card lengths, wrong CVCs, and expired dates.

#### `SubscriptionCardOnlyTest.java` (1 Test)
- Layer: Mockito Unit Test
- Result: 1 / 1 Passed

1. `gatePaymentCannotCreateNewPackageSubscription`: Rejects cash/gate payment for new package subscriptions.

---

### 4.6 Package & Discount Module (`com.example.parkngo.discount`)

#### `PackageServiceTest.java` (14 Tests)
- Layer: Mockito Unit Test
- Result: 14 / 14 Passed

1. `savePackage_create_persistsNewPackageWhenNameIsUnique`: Creates unique package.
2. `savePackage_create_rejectsDuplicateName`: Rejects duplicate package name.
3. `savePackage_trimsNameBeforeCheckingAndSaving`: Trims whitespace from package name.
4. `savePackage_update_appliesChangesToExistingPackage`: Modifies package fields.
5. `savePackage_update_rejectsNameCollisionWithAnotherPackage`: Prevents renaming to existing package name.
6. `savePackage_update_throwsWhenTargetPackageMissing`: Throws not found when package is missing.
7. `toggleStatus_flipsActivePackageToInactive`: Toggles package between active and inactive.
8. `toggleStatus_throwsWhenPackageMissing`: Throws exception on missing package toggle.
9. `deletePackage_throwsWhenReferencedBySubscriptions`: Blocks deletion if customer passes reference it.
10. `deletePackage_translatesForeignKeyViolationIntoResourceInUse`: Translates FK exception to friendly error.
11. `deletePackage_succeedsWhenNotReferenced`: Deletes package without subscriptions.
12. `createPackage_rejectsNegativePrice`: Rejects negative price.
13. `createPackage_rejectsZeroPrice`: Rejects zero price.
14. `createPackage_acceptsPositivePrice`: Accepts valid positive price.

#### `DiscountServiceMockitoTest.java` (8 Tests)
- Layer: Mockito Unit Test
- Result: 8 / 8 Passed

1. `saveDiscount_create_normalizesCodeToUppercaseAndPersists`: Uppercases discount code.
2. `saveDiscount_create_rejectsDuplicateCode`: Rejects duplicate code.
3. `saveDiscount_rejectsExpiryBeforeStartDate`: Rejects invalid date order.
4. `saveDiscount_rejectsPercentageOver100`: Rejects percentage > 100%.
5. `saveDiscount_allowsFixedAmountOver100`: Permits fixed discounts over LKR 100.
6. `saveDiscount_update_throwsWhenTargetMissing`: Throws not found on missing discount.
7. `deleteDiscount_translatesForeignKeyViolationIntoResourceInUse`: Handles FK deletion gracefully.
8. `calculateDiscount_calculatesPercentageAndFixedAmounts`: Verifies discount math.

#### `DiscountServiceTest.java` (5 Tests)
- Layer: `@SpringBootTest` Integration Test
- Result: 5 / 5 Passed

1. `testCreateDiscount`: Saves and retrieves discount from live database.
2. `testDuplicateCodePrevention`: Database constraint prevents duplicate promo codes.
3. `testPercentageDiscountCalculation`: Calculates percentage discount in database.
4. `testGetDiscountByCode`: Queries active discount by promo code.
5. `testIsValidDiscount`: Checks discount date validity.

---

### 4.7 Reservations & Vehicle Module (`com.example.parkngo.reservation` & `com.example.parkngo.vehicle`)

#### `ReservationServiceMockitoTest.java` (6 Tests)
- Layer: Mockito Unit Test
- Result: 6 / 6 Passed

1. `testCreateReservationUpdatesSpaceAndOccupancy`: Marks space RESERVED without changing physical occupancy.
2. `testCancelReservationFreesSpaceAndDecrementsOccupancy`: Marks space AVAILABLE on cancellation.
3. `testCheckOutFreesSpaceAndDecrementsOccupancy`: Frees space to AVAILABLE and decrements facility occupancy by 1.
4. `testBookAndPayReservationWithOnArrivalPayment`: Dispatches `"On-Arrival Payment"` notification.
5. `testBookAndPayReservationWithOnlinePayment`: Dispatches `"Booking Confirmed & Paid"` and `"Payment Received"` notifications.
6. `testVerifyAndCheckInSettlesOnSitePaymentAndSendsNotification`: Settles gate payment and sends check-in notification.

#### `ReservationServiceTest.java` (21 Tests)
- Layer: `@SpringBootTest` Integration Test
- Result: 21 / 21 Passed

1. `testCreateReservation`: Saves reservation with generated code.
2. `testAvailabilityCheck`: Verifies space availability for date range.
3. `testPreventDoubleBookingOnSameSpace`: Prevents overlapping bookings on same bay.
4. `testPreventDoubleBookingSameVehicleAcrossBays`: Prevents booking same vehicle in multiple bays simultaneously.
5. `testInvalidEmailValidation`: Rejects invalid email.
6. `testInvalidPhoneValidation`: Rejects invalid contact number.
7. `testPastArrivalTimeValidation`: Rejects booking starting in the past.
8. `testMinimumStayDurationValidation`: Requires stay of at least 30 minutes.
9. `testReservationCancellation`: Cancels reservation and releases slot.
10. `testBookAndPayReservation`: Confirms booking and records card payment.
11. `testCancelReservationRefundsPayment`: Issues refund on cancellation of paid booking.
12. `testUpdateReservationWithVehicleChange`: Updates license plate on existing booking.
13. `testTurnoverBufferEnforcement`: Enforces 15-minute turnover buffer.
14. `testEarlyCheckInCutoff`: Prevents check-in before allowed arrival window.
15. `testEditReservationWithDurationExtensionAndCardPayment`: Extensions charge additional card fee.
16. `testEditReservationWithDurationReductionAndCardRefund`: Reductions issue partial refund.
17. `testEditReservationPayAtGateAndCancel`: Modifies and cancels gate payment bookings.
18. `testEditReservationAddAndRemoveAddonService`: Adds/removes services and updates total.
19. `testBookAndPayReservation_InvalidCardLuhnChecksumFails`: Rejects bad Luhn checksum.
20. `testBookAndPayReservation_ExpiredCardFails`: Rejects expired credit card.
21. `testBookAndPayReservation_InvalidCvcFails`: Rejects bad security code.

#### `VehicleServiceTest.java` (8 Tests)
- Layer: `@SpringBootTest` Integration Test
- Result: 8 / 8 Passed

1. `testRegisterVehicle`: Registers vehicle to customer profile.
2. `testDuplicateRegistrationPrevention`: Prevents registering duplicate license plate.
3. `testGetCustomerVehicles`: Returns all vehicles for authenticated customer.
4. `testUpdateVehicle`: Updates vehicle make, model, color.
5. `testUpdateVehicleDuplicatePlatePrevention`: Prevents changing plate to existing plate.
6. `testCustomerOwnershipIsolation`: Prevents cross-customer vehicle access.
7. `testDeleteVehicle`: Deletes vehicle without active bookings.
8. `testIsVehicleRegistered`: Checks if license plate exists.

---

## 5. Verification Command & Output

```
mvn test
```

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 171, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
