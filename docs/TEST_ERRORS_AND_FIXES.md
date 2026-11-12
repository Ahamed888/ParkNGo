# ParkNGo Test Errors and Code Fixes Documentation

---

## 1. Overview

This document provides a detailed record of every test failure and error encountered in the **ParkNGo** test suite, the underlying root causes, the exact files and lines modified to fix each issue, and verification of the 100% pass rate.

### Test Execution Summary

| State | Test Classes | Test Cases | Failures | Errors | Status |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Before Fixes** | 26 | 171 | 1 | 34 | BUILD FAILURE |
| **After Fixes** | 26 | 171 | 0 | 0 | **BUILD SUCCESS (100% Passing)** |

---

## 2. Itemized Error Diagnostics & Code Fixes

---

### Issue 1: MSSQL Database Connection Failure in Spring Boot Integration Tests

#### Symptoms & Error Log
When running integration tests annotated with `@SpringBootTest`, Spring Boot failed to initialize the ApplicationContext:
```text
[ERROR] VehicleServiceTest.testRegisterVehicle -> IllegalState ApplicationContext failure threshold (1) exceeded: skipping repeated attempt to load context
[ERROR] DiscountServiceTest -> JDBCConnectionException: Unable to open JDBC Connection for DDL execution
Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: The TCP/IP connection to host localhost, port 1433 has failed.
Error: "Operation not permitted"
```

#### Affected Test Classes
1. `src/test/java/com/example/parkngo/vehicle/VehicleServiceTest.java` (8 tests)
2. `src/test/java/com/example/parkngo/discount/DiscountServiceTest.java` (5 tests)
3. `src/test/java/com/example/parkngo/reservation/ReservationServiceTest.java` (21 tests)
4. `src/test/java/com/example/parkngo/security/LogoutFlowWebTest.java` (5 tests)

#### Root Causes
1. **Sandbox Socket Isolation:** Running commands in an isolated execution sandbox blocked local TCP network sockets from reaching `localhost:1433`.
2. **Missing Catalog in SQL Server:** The test database `parkngo_test` did not exist inside Docker container `sqlserver2022`.
3. **Missing Fallback Credentials:** `application-test.properties` referenced `${PARKNGO_TEST_DB_USERNAME}` and `${PARKNGO_TEST_DB_PASSWORD}` without default values, causing unauthenticated connection failures if environment variables were unset.

#### Changes Applied
1. Created catalog `parkngo_test` in SQL Server:
   ```sql
   IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'parkngo_test')
   BEGIN
       CREATE DATABASE parkngo_test;
   END
   ```
2. Updated `src/test/resources/application-test.properties` with fallback credentials:
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=parkngo_test;encrypt=true;trustServerCertificate=true
   spring.datasource.username=${PARKNGO_TEST_DB_USERNAME:SA}
   spring.datasource.password=${PARKNGO_TEST_DB_PASSWORD}
   ```

---

### Issue 2: JPA Hibernate Physical Naming Strategy Column Casing Conflict

#### Symptoms & Error Log
```text
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataInitializer': Invocation of init method failed
Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: Invalid column name 'RoleName'.
```

#### Affected Test Classes
All 4 `@SpringBootTest` test classes during application startup (`DataInitializer.java`).

#### Root Cause
Production `application.properties` specifies `org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl` to keep PascalCase database columns (`RoleName`, `FacilityID`, `UserID`). However, `application-test.properties` lacked this setting, causing Hibernate in test mode to use Spring's default snake_case (`role_name`, `facility_id`). Raw SQL statements in `DataInitializer` expected `RoleName` and failed.

#### Changes Applied
Added the standard naming strategy to `src/test/resources/application-test.properties`:
```properties
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

---

### Issue 3: Missing "Payment Received" Notification in Online Reservation Workflow

#### Symptoms & Error Log
```text
[ERROR] Failures: 1
[ERROR]   ReservationServiceMockitoTest.testBookAndPayReservationWithOnlinePayment:173
Wanted but not invoked:
notificationService.sendNotification(
    <any com.example.parkngo.user.User>,
    "Payment Received",
    <any String>,
    PAYMENT_SUCCESSFUL,
    <any Long>
);
-> at com.example.parkngo.reservation.ReservationServiceMockitoTest.testBookAndPayReservationWithOnlinePayment(ReservationServiceMockitoTest.java:173)
Actually, there were zero interactions with this mock.
```

#### Affected Test Class
`src/test/java/com/example/parkngo/reservation/ReservationServiceMockitoTest.java`

#### Root Cause
In `ReservationService.java`, the method `bookAndPayReservation()` only dispatched a single notification: `"Booking Confirmed & Paid"` (`NotificationType.BOOKING_CONFIRMED`). However, `ReservationServiceMockitoTest` asserts that online card transactions dispatch two distinct notifications:
1. Booking confirmation (`BOOKING_CONFIRMED`)
2. Payment receipt (`PAYMENT_SUCCESSFUL`) with title `"Payment Received"`

#### Changes Applied
Modified `src/main/java/com/example/parkngo/reservation/ReservationService.java` inside `bookAndPayReservation()`:

```java
// Added payment receipt notification dispatch for online card payments
if (paymentMethod == PaymentMethod.CREDIT_CARD || paymentMethod == PaymentMethod.DEBIT_CARD) {
    notificationService.sendNotification(
        user,
        "Payment Received",
        "Your payment of LKR " + res.getTotalAmount() + " for reservation #" + res.getReservationCode() + " has been received successfully.",
        NotificationType.PAYMENT_SUCCESSFUL,
        res.getId()
    );
}
```

---

### Issue 4: Standalone Thymeleaf `#fields.hasGlobalErrors()` Evaluation Failure

#### Symptoms & Error Log
```text
[ERROR] Errors: 1
[ERROR]   RedesignTemplateTest.redesignedPagesRenderWithPopulatedAndEmptyData:85 -> TemplateProcessing
Exception evaluating SpringEL expression: "#fields.hasGlobalErrors()" (template: "admin/package-form" - line 28, col 23)
Caused by: java.lang.IllegalStateException: Neither BindingResult nor plain target object for bean name 'membershipPackage' available as request attribute
```

#### Affected Test Class
`src/test/java/com/example/parkngo/space/RedesignTemplateTest.java`

#### Root Cause
`RedesignTemplateTest` tests Thymeleaf templates offline using `new SpringTemplateEngine()`. In Spring Boot 3 / Thymeleaf 3, `#fields` expression evaluation relies on Spring MVC's request context infrastructure. Because `MockServletContext` was not linked to a `WebApplicationContext` and `thymeleafRequestContext` was not present in the model, `#fields.hasGlobalErrors()` threw `IllegalStateException`.

#### Changes Applied
Modified `src/test/java/com/example/parkngo/space/RedesignTemplateTest.java` in `render(...)`:

```java
// Configure Mock Web Application Context for Spring MVC Thymeleaf rendering
org.springframework.web.context.support.GenericWebApplicationContext appContext = 
    new org.springframework.web.context.support.GenericWebApplicationContext();
appContext.setServletContext(servletContext);
appContext.refresh();
servletContext.setAttribute(
    org.springframework.web.context.WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, 
    appContext
);
request.setAttribute(
    org.springframework.web.servlet.DispatcherServlet.WEB_APPLICATION_CONTEXT_ATTRIBUTE, 
    appContext
);

org.springframework.web.servlet.support.RequestContext springRequestContext = 
    new org.springframework.web.servlet.support.RequestContext(request, response, servletContext, model);
model.put(
    org.thymeleaf.spring6.naming.SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT, 
    new org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext(springRequestContext, request)
);
```

---

### Issue 5: Foreign Key Violation During Test Setup Cleanup in `VehicleServiceTest`

#### Symptoms & Error Log
```text
org.springframework.dao.DataIntegrityViolationException: could not execute statement [The DELETE statement conflicted with the REFERENCE constraint "FK_Reservation_User". The conflict occurred in database "parkngo_test", table "dbo.Reservation", column 'UserID'.]
[SQL: delete from Users where UserID=?]
```

#### Affected Test Class
`src/test/java/com/example/parkngo/vehicle/VehicleServiceTest.java`

#### Root Cause
The `@BeforeEach setUp()` method in `VehicleServiceTest.java` previously called `userRepository.deleteAll()`. Because other seeded records, reservations, or audit logs held foreign key references to users in the persistent database, SQL Server blocked the deletion, aborting all 8 vehicle service tests.

#### Changes Applied
Modified `src/test/java/com/example/parkngo/vehicle/VehicleServiceTest.java`:
Replaced destructive `userRepository.deleteAll()` with safe lookup and creation:

```java
// Safely find existing test user or create if absent
testUser = userRepository.findByEmail("test@example.com")
        .orElseGet(() -> {
            User u = new User();
            u.setEmail("test@example.com");
            u.setFullName("Test User");
            u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuu");
            u.setPhoneNumber("0771234567");
            u.setUserType(User.UserType.CUSTOMER);
            return userRepository.save(u);
        });
```

---

## 3. Summary of Files Changed

| Modified File | Primary Change Description |
| :--- | :--- |
| `src/test/resources/application-test.properties` | Uses environment-provided test credentials and `PhysicalNamingStrategyStandardImpl` |
| `src/main/java/com/example/parkngo/reservation/ReservationService.java` | Added `"Payment Received"` notification dispatch for online card payment transactions |
| `src/test/java/com/example/parkngo/space/RedesignTemplateTest.java` | Registered `GenericWebApplicationContext` and `SpringWebMvcThymeleafRequestContext` for `#fields` |
| `src/test/java/com/example/parkngo/vehicle/VehicleServiceTest.java` | Refactored `setUp()` to find-or-create test users instead of truncating table |

---

## 4. Verification & Validation Result

Executing `mvn test` confirms that all 26 test classes pass cleanly with 0 errors:

```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 171, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.933 s
```
