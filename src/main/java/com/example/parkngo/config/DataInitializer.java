package com.example.parkngo.config;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final AdminRepository adminRepository;
    private final ParkingFacilityRepository parkingFacilityRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private FacilityStaffAccountService facilityStaffAccountService;

    @Autowired
    public DataInitializer(RoleRepository roleRepository, 
                           StaffRepository staffRepository,
                           CustomerRepository customerRepository,
                           AdminRepository adminRepository,
                           ParkingFacilityRepository parkingFacilityRepository,
                           ParkingSpaceRepository parkingSpaceRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.roleRepository = roleRepository;
        this.staffRepository = staffRepository;
        this.customerRepository = customerRepository;
        this.adminRepository = adminRepository;
        this.parkingFacilityRepository = parkingFacilityRepository;
        this.parkingSpaceRepository = parkingSpaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initDatabaseSchema() {
        if (jdbcTemplate == null) return;

        try {
            jdbcTemplate.execute("IF OBJECT_ID(N'dbo.[User]', N'U') IS NOT NULL " +
                "AND COL_LENGTH(N'dbo.[User]', N'UserType') IS NULL " +
                "ALTER TABLE dbo.[User] ADD UserType VARCHAR(20) NOT NULL " +
                "CONSTRAINT DF_User_UserType DEFAULT ('CUSTOMER') WITH VALUES");
        } catch (Exception exception) {
            logger.error("Unable to add the required UserType column to dbo.[User]", exception);
            throw new IllegalStateException("Database schema migration failed for dbo.[User].UserType", exception);
        }

        // 1. [User] table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('[User]') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('[User]') AND name = 'UserType') " +
                "    ALTER TABLE [User] ADD UserType VARCHAR(20) NOT NULL CONSTRAINT DF_User_UserType DEFAULT ('CUSTOMER') WITH VALUES; " +
                "IF OBJECT_ID('Admin') IS NOT NULL UPDATE u SET UserType = 'ADMIN' FROM [User] u INNER JOIN Admin a ON a.UserID = u.UserID; " +
                "IF OBJECT_ID('Staff') IS NOT NULL UPDATE u SET UserType = 'STAFF' FROM [User] u INNER JOIN Staff s ON s.UserID = u.UserID; " +
                "IF OBJECT_ID('Customer') IS NOT NULL UPDATE u SET UserType = 'CUSTOMER' FROM [User] u INNER JOIN Customer c ON c.UserID = u.UserID; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('[User]') AND name = 'ProfilePicture') ALTER TABLE [User] ADD ProfilePicture NVARCHAR(MAX) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('[User]') AND name = 'ShiftHours') ALTER TABLE [User] ADD ShiftHours NVARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('[User]') AND name = 'UpdatedAt') ALTER TABLE [User] ADD UpdatedAt DATETIME2 NULL; " +
                "IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('[User]') AND name = 'UserType') BEGIN " +
                "    IF NOT EXISTS (SELECT 1 FROM sys.default_constraints dc JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id WHERE parent_object_id = OBJECT_ID('[User]') AND c.name = 'UserType') " +
                "    BEGIN ALTER TABLE [User] ADD CONSTRAINT DF_User_UserType DEFAULT ('CUSTOMER') FOR UserType; END " +
                "END; " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_User_Status') ALTER TABLE [User] DROP CONSTRAINT CK_User_Status; " +
                "ALTER TABLE [User] ADD CONSTRAINT CK_User_Status CHECK ([Status] IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')); " +
                "END");
        } catch (Exception ignored) {}

        // 1b. Password reset token storage
        try {
            jdbcTemplate.execute("IF OBJECT_ID('dbo.PasswordResetToken', 'U') IS NULL BEGIN " +
                "CREATE TABLE dbo.PasswordResetToken (" +
                "TokenID BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PasswordResetToken PRIMARY KEY, " +
                "UserID BIGINT NOT NULL, " +
                "TokenHash CHAR(64) NOT NULL CONSTRAINT UQ_PasswordResetToken_TokenHash UNIQUE, " +
                "ExpiresAt DATETIME2 NOT NULL, " +
                "UsedAt DATETIME2 NULL, " +
                "CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_PasswordResetToken_CreatedAt DEFAULT SYSDATETIME(), " +
                "CONSTRAINT FK_PasswordResetToken_User FOREIGN KEY (UserID) REFERENCES dbo.[User](UserID) ON DELETE CASCADE" +
                "); " +
                "CREATE INDEX IX_PasswordResetToken_UserID ON dbo.PasswordResetToken(UserID); " +
                "END");
        } catch (Exception exception) {
            logger.error("Unable to initialize password reset token storage", exception);
            throw new IllegalStateException("Database schema migration failed for dbo.PasswordResetToken", exception);
        }

        // 2. Admin table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Admin') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Admin') AND name = 'Department') ALTER TABLE Admin ADD Department VARCHAR(50) NULL; " +
                "END");
        } catch (Exception ignored) {}

        // 3. Customer table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Customer') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Customer') AND name = 'LoyaltyPoints') ALTER TABLE Customer ADD LoyaltyPoints INT NOT NULL DEFAULT 0; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Customer') AND name = 'MembershipTier') ALTER TABLE Customer ADD MembershipTier VARCHAR(30) NOT NULL DEFAULT 'BRONZE'; " +
                "END");
        } catch (Exception ignored) {}

        // 4. Staff table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Staff') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Staff') AND name = 'FacilityID') ALTER TABLE Staff ADD FacilityID INT NULL; " +
                "END");
        } catch (Exception ignored) {}

        // 4b. Vehicle table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Vehicle') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Vehicle') AND name = 'CreatedAt') ALTER TABLE Vehicle ADD CreatedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(); " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Vehicle_Type') ALTER TABLE Vehicle DROP CONSTRAINT CK_Vehicle_Type; " +
                "ALTER TABLE Vehicle ADD CONSTRAINT CK_Vehicle_Type CHECK (VehicleType IN ('CAR', 'SEDAN', 'SUV', 'VAN', 'MOTORCYCLE', 'BIKE', 'EV', 'ELECTRIC', 'TRUCK')); " +
                "IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Vehicle') AND name = 'Color' AND is_nullable = 0) ALTER TABLE Vehicle ALTER COLUMN Color VARCHAR(30) NULL; " +
                "END");
        } catch (Exception ignored) {}

        // 5. Discount table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Discount') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'AppliesTo') ALTER TABLE Discount ADD AppliesTo VARCHAR(30) NOT NULL DEFAULT 'ALL'; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'MaxUses') ALTER TABLE Discount ADD MaxUses INT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'MaxUsesPerCustomer') ALTER TABLE Discount ADD MaxUsesPerCustomer INT NULL DEFAULT 1; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'UsesCount') ALTER TABLE Discount ADD UsesCount INT NOT NULL DEFAULT 0; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'OffPeakStartHour') ALTER TABLE Discount ADD OffPeakStartHour INT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'OffPeakEndHour') ALTER TABLE Discount ADD OffPeakEndHour INT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Discount') AND name = 'CreatedAt') ALTER TABLE Discount ADD CreatedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(); " +
                "END");
        } catch (Exception ignored) {}

        // 6. ParkingSpace table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('ParkingSpace') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingSpace') AND name = 'AllocationType') ALTER TABLE ParkingSpace ADD AllocationType VARCHAR(50) DEFAULT 'GENERAL' NOT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingSpace') AND name = 'PendingReleaseAt') ALTER TABLE ParkingSpace ADD PendingReleaseAt DATETIME2 NULL; " +
                "END");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute(
                "IF OBJECT_ID('dbo.ParkingSpace', 'U') IS NOT NULL " +
                "BEGIN " +
                "DECLARE @constraintName NVARCHAR(128); " +
                "SELECT TOP 1 @constraintName = cc.name " +
                "FROM sys.check_constraints cc " +
                "WHERE cc.parent_object_id = OBJECT_ID('dbo.ParkingSpace') " +
                "AND cc.definition LIKE '%Status%'; " +
                "IF @constraintName IS NOT NULL " +
                "BEGIN " +
                "    DECLARE @dropSql NVARCHAR(MAX); " +
                "    SET @dropSql = N'ALTER TABLE dbo.ParkingSpace DROP CONSTRAINT ' + QUOTENAME(@constraintName); " +
                "    EXEC sys.sp_executesql @dropSql; " +
                "END; " +
                "ALTER TABLE dbo.ParkingSpace ADD CONSTRAINT CK_ParkingSpace_Status " +
                "CHECK ([Status] IN ('AVAILABLE', 'RESERVED', 'OCCUPIED', 'MAINTENANCE', 'OUT_OF_SERVICE')); " +
                "END");
        } catch (Exception exception) {
            logger.error("Unable to synchronize dbo.ParkingSpace.Status values", exception);
            throw new IllegalStateException("Database schema migration failed for dbo.ParkingSpace.Status", exception);
        }

        // 7. Payment table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Payment') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'UserID') ALTER TABLE Payment ADD UserID BIGINT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'SubscriptionID') ALTER TABLE Payment ADD SubscriptionID BIGINT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'CardBrand') ALTER TABLE Payment ADD CardBrand VARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'MaskedCardNumber') ALTER TABLE Payment ADD MaskedCardNumber VARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'PaymentType') ALTER TABLE Payment ADD PaymentType VARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'FailureReason') ALTER TABLE Payment ADD FailureReason NVARCHAR(MAX) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'Remarks') ALTER TABLE Payment ADD Remarks NVARCHAR(MAX) NULL; " +
                "IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Payment') AND name = 'ReservationID' AND is_nullable = 0) ALTER TABLE Payment ALTER COLUMN ReservationID INT NULL; " +
                "END");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_Payment_ReservationID') ALTER TABLE Payment DROP CONSTRAINT UQ_Payment_ReservationID;");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Payment') IS NOT NULL BEGIN " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Payment_Status') ALTER TABLE Payment DROP CONSTRAINT CK_Payment_Status; " +
                "ALTER TABLE Payment ADD CONSTRAINT CK_Payment_Status CHECK ([Status] IN ('PENDING', 'PAID', 'COMPLETED', 'FAILED', 'REFUNDED')); " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Payment_Method') ALTER TABLE Payment DROP CONSTRAINT CK_Payment_Method; " +
                "END");
        } catch (Exception ignored) {}
        // The original schema's PaymentMethod CHECK has an auto-generated name
        // (e.g. CK__Payment__Payment__xxxx) and omits values such as CASH, so find
        // it by column rather than by name, then recreate it from the Java enum.
        try {
            String allowedMethods = java.util.Arrays.stream(com.example.parkngo.payment.Payment.PaymentMethod.values())
                    .map(m -> "'" + m.name() + "'")
                    .collect(java.util.stream.Collectors.joining(", "));
            jdbcTemplate.execute("IF OBJECT_ID('dbo.Payment', 'U') IS NOT NULL BEGIN " +
                "DECLARE @ckName NVARCHAR(128); " +
                "SELECT TOP 1 @ckName = cc.name FROM sys.check_constraints cc " +
                "  WHERE cc.parent_object_id = OBJECT_ID('dbo.Payment') " +
                "  AND cc.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Payment') AND name = 'PaymentMethod'); " +
                "WHILE @ckName IS NOT NULL BEGIN " +
                "  DECLARE @dropCk NVARCHAR(MAX) = N'ALTER TABLE dbo.Payment DROP CONSTRAINT ' + QUOTENAME(@ckName); " +
                "  EXEC sys.sp_executesql @dropCk; " +
                "  SET @ckName = NULL; " +
                "  SELECT TOP 1 @ckName = cc.name FROM sys.check_constraints cc " +
                "    WHERE cc.parent_object_id = OBJECT_ID('dbo.Payment') " +
                "    AND cc.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Payment') AND name = 'PaymentMethod'); " +
                "END; " +
                "ALTER TABLE dbo.Payment ADD CONSTRAINT CK_Payment_Method CHECK ([PaymentMethod] IN (" + allowedMethods + ")); " +
                "END");
        } catch (Exception exception) {
            logger.error("Unable to synchronize dbo.Payment.PaymentMethod check constraint", exception);
        }

        // Catalog removal preserves service references on reservations and memberships.
        try {
            jdbcTemplate.execute("IF OBJECT_ID(N'dbo.FacilityService', N'U') IS NOT NULL " +
                    "AND COL_LENGTH(N'dbo.FacilityService', N'Deleted') IS NULL " +
                    "ALTER TABLE dbo.FacilityService ADD Deleted BIT NOT NULL CONSTRAINT DF_FacilityService_Deleted DEFAULT 0 WITH VALUES");
        } catch (Exception exception) {
            throw new IllegalStateException("Database schema migration failed for dbo.FacilityService.Deleted", exception);
        }
        // FacilityService.Status: the app persists ACTIVE/INACTIVE (see
        // ServiceStatusConverter) but the original schema's auto-named CHECK
        // rejects them. Find it by column, drop it, and allow every spelling.
        try {
            jdbcTemplate.execute("IF OBJECT_ID('dbo.FacilityService', 'U') IS NOT NULL BEGIN " +
                "DECLARE @fsCk NVARCHAR(128); " +
                "SELECT TOP 1 @fsCk = cc.name FROM sys.check_constraints cc " +
                "  WHERE cc.parent_object_id = OBJECT_ID('dbo.FacilityService') " +
                "  AND cc.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('dbo.FacilityService') AND name = 'Status'); " +
                "WHILE @fsCk IS NOT NULL BEGIN " +
                "  DECLARE @fsDrop NVARCHAR(MAX) = N'ALTER TABLE dbo.FacilityService DROP CONSTRAINT ' + QUOTENAME(@fsCk); " +
                "  EXEC sys.sp_executesql @fsDrop; " +
                "  SET @fsCk = NULL; " +
                "  SELECT TOP 1 @fsCk = cc.name FROM sys.check_constraints cc " +
                "    WHERE cc.parent_object_id = OBJECT_ID('dbo.FacilityService') " +
                "    AND cc.parent_column_id = (SELECT column_id FROM sys.columns WHERE object_id = OBJECT_ID('dbo.FacilityService') AND name = 'Status'); " +
                "END; " +
                "ALTER TABLE dbo.FacilityService ADD CONSTRAINT CK_FacilityService_Status CHECK ([Status] IN ('ACTIVE', 'INACTIVE', 'AVAILABLE', 'UNAVAILABLE')); " +
                "END");
        } catch (Exception exception) {
            logger.error("Unable to synchronize dbo.FacilityService.Status check constraint", exception);
        }

        // FacilityServiceFacility join table used by FacilityService.facilities (ManyToMany).
        try {
            jdbcTemplate.execute(
                "IF OBJECT_ID('dbo.FacilityServiceFacility', 'U') IS NULL BEGIN " +
                "  CREATE TABLE dbo.FacilityServiceFacility ( " +
                "    ServiceID BIGINT NOT NULL, " +
                "    FacilityID BIGINT NOT NULL, " +
                "    CONSTRAINT PK_FacilityServiceFacility PRIMARY KEY (ServiceID, FacilityID) " +
                "  ); " +
                "END; " +
                "IF COL_LENGTH('dbo.FacilityService', 'FacilityID') IS NOT NULL BEGIN " +
                "  EXEC(' " +
                "    INSERT INTO dbo.FacilityServiceFacility (ServiceID, FacilityID) " +
                "    SELECT fs.ServiceID, fs.FacilityID " +
                "    FROM dbo.FacilityService fs " +
                "    WHERE fs.FacilityID IS NOT NULL " +
                "      AND NOT EXISTS ( " +
                "          SELECT 1 " +
                "          FROM dbo.FacilityServiceFacility fsf " +
                "          WHERE fsf.ServiceID = fs.ServiceID " +
                "            AND fsf.FacilityID = fs.FacilityID " +
                "      ); " +
                "    DECLARE @facilityFk sysname; " +
                "    SELECT @facilityFk = fk.name " +
                "    FROM sys.foreign_keys fk " +
                "    JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id " +
                "    JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id " +
                "    WHERE fk.parent_object_id = OBJECT_ID(''dbo.FacilityService'') AND c.name = ''FacilityID''; " +
                "    IF @facilityFk IS NOT NULL " +
                "        EXEC(''ALTER TABLE dbo.FacilityService DROP CONSTRAINT ['' + @facilityFk + '']''); " +
                "    ALTER TABLE dbo.FacilityService DROP COLUMN FacilityID; " +
                "  '); " +
                "END");
        } catch (Exception exception) {
            logger.error("Unable to synchronize dbo.FacilityServiceFacility", exception);
        }

        // 8. Reservation table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Reservation') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'ReservationCode') ALTER TABLE Reservation ADD ReservationCode VARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'AppliedSubscriptionID') ALTER TABLE Reservation ADD AppliedSubscriptionID BIGINT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'ActualArrivalTime') ALTER TABLE Reservation ADD ActualArrivalTime DATETIME2 NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'ActualDepartureTime') ALTER TABLE Reservation ADD ActualDepartureTime DATETIME2 NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'ContactEmail') ALTER TABLE Reservation ADD ContactEmail VARCHAR(100) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'ContactPhone') ALTER TABLE Reservation ADD ContactPhone VARCHAR(30) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'GuestName') ALTER TABLE Reservation ADD GuestName VARCHAR(100) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'IsWalkIn') ALTER TABLE Reservation ADD IsWalkIn BIT NULL DEFAULT 0; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'VerifiedByStaffID') ALTER TABLE Reservation ADD VerifiedByStaffID BIGINT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'VerifiedAt') ALTER TABLE Reservation ADD VerifiedAt DATETIME2 NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'UpdatedAt') ALTER TABLE Reservation ADD UpdatedAt DATETIME2 NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'PaymentHoldExpiresAt') ALTER TABLE Reservation ADD PaymentHoldExpiresAt DATETIME2 NULL; " +
                "IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Reservation') AND name = 'ReservationType') BEGIN " +
                "    IF NOT EXISTS (SELECT 1 FROM sys.default_constraints dc JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id WHERE parent_object_id = OBJECT_ID('Reservation') AND c.name = 'ReservationType') " +
                "    BEGIN ALTER TABLE Reservation ADD CONSTRAINT DF_Reservation_ReservationType DEFAULT ('ONLINE') FOR ReservationType; END " +
                "END; " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Res_Status') ALTER TABLE Reservation DROP CONSTRAINT CK_Res_Status; " +
                "ALTER TABLE Reservation ADD CONSTRAINT CK_Res_Status CHECK ([Status] IN ('PENDING', 'CONFIRMED', 'VERIFIED', 'ACTIVE', 'OVERSTAYING', 'OVERSTAY_PAYMENT_DUE', 'COMPLETED', 'CANCELLED', 'EXPIRED')); " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Res_ISA') ALTER TABLE Reservation DROP CONSTRAINT CK_Res_ISA; " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Reservation_ISA') ALTER TABLE Reservation DROP CONSTRAINT CK_Reservation_ISA; " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Reservation_Type') ALTER TABLE Reservation DROP CONSTRAINT CK_Reservation_Type; " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Res_Type') ALTER TABLE Reservation DROP CONSTRAINT CK_Res_Type; " +
                "DECLARE @ckRes NVARCHAR(128); " +
                "SELECT TOP 1 @ckRes = cc.name FROM sys.check_constraints cc WHERE cc.parent_object_id = OBJECT_ID('dbo.Reservation') AND (cc.name LIKE '%ISA%' OR cc.name LIKE '%Type%'); " +
                "WHILE @ckRes IS NOT NULL BEGIN " +
                "  DECLARE @dropCk NVARCHAR(MAX) = N'ALTER TABLE dbo.Reservation DROP CONSTRAINT ' + QUOTENAME(@ckRes); " +
                "  EXEC sys.sp_executesql @dropCk; " +
                "  SET @ckRes = NULL; " +
                "  SELECT TOP 1 @ckRes = cc.name FROM sys.check_constraints cc WHERE cc.parent_object_id = OBJECT_ID('dbo.Reservation') AND (cc.name LIKE '%ISA%' OR cc.name LIKE '%Type%'); " +
                "END; " +
                "END");
        } catch (Exception ignored) {}

        // 8b. Walk-in passes have no registered vehicle/customer/user: the guest
        // plate is stored on WalkInReservation and the cash payment has no
        // account. Relax NOT NULL on these columns (keeping their type).
        for (String target : new String[]{"Reservation.VehicleID", "Reservation.CustomerID", "Payment.UserID"}) {
            String table = target.substring(0, target.indexOf('.'));
            String column = target.substring(target.indexOf('.') + 1);
            try {
                jdbcTemplate.execute(
                    "IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo." + table + "') AND name = '" + column + "' AND is_nullable = 0) " +
                    "BEGIN " +
                    "  DECLARE @type NVARCHAR(128); " +
                    "  SELECT @type = t.name FROM sys.columns c JOIN sys.types t ON c.user_type_id = t.user_type_id " +
                    "  WHERE c.object_id = OBJECT_ID('dbo." + table + "') AND c.name = '" + column + "'; " +
                    "  DECLARE @sql NVARCHAR(MAX) = N'ALTER TABLE dbo." + table + " ALTER COLUMN " + column + " ' + @type + N' NULL'; " +
                    "  EXEC sys.sp_executesql @sql; " +
                    "END");
            } catch (Exception exception) {
                logger.error("Unable to make dbo.{}.{} nullable (required for walk-in passes)", table, column, exception);
            }
        }

        // 9. Package table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID(N'dbo.[Package]', N'U') IS NOT NULL " +
                    "AND COL_LENGTH(N'dbo.[Package]', N'Deleted') IS NULL " +
                    "ALTER TABLE dbo.[Package] ADD Deleted BIT NOT NULL CONSTRAINT DF_Package_Deleted DEFAULT 0 WITH VALUES");
        } catch (Exception exception) {
            throw new IllegalStateException("Database schema migration failed for dbo.Package.Deleted", exception);
        }
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Package') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Package') AND name = 'VisitCount') ALTER TABLE Package ADD VisitCount INT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Package') AND name = 'IncludedServices') ALTER TABLE Package ADD IncludedServices NVARCHAR(MAX) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Package') AND name = 'AllFacilities') ALTER TABLE Package ADD AllFacilities BIT NOT NULL CONSTRAINT DF_Package_AllFacilities DEFAULT 1; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Package') AND name = 'Tier') ALTER TABLE Package ADD Tier VARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Package') AND name = 'CreatedAt') ALTER TABLE Package ADD CreatedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(); " +
                "END");
        } catch (Exception ignored) {}

        // 9b. Package join tables used by Package.facilities and
        // Package.includedServiceItems (ManyToMany). Created without FKs so they
        // work regardless of whether the existing ID columns are INT or BIGINT.
        try {
            jdbcTemplate.execute("IF OBJECT_ID('dbo.PackageFacility', 'U') IS NULL BEGIN " +
                "CREATE TABLE dbo.PackageFacility (" +
                "PackageID BIGINT NOT NULL, " +
                "FacilityID BIGINT NOT NULL, " +
                "CONSTRAINT PK_PackageFacility PRIMARY KEY (PackageID, FacilityID)" +
                "); END");
        } catch (Exception exception) {
            logger.error("Unable to create dbo.PackageFacility", exception);
        }
        try {
            jdbcTemplate.execute("IF OBJECT_ID('dbo.PackageIncludedService', 'U') IS NULL BEGIN " +
                "CREATE TABLE dbo.PackageIncludedService (" +
                "PackageID BIGINT NOT NULL, " +
                "ServiceID BIGINT NOT NULL, " +
                "CONSTRAINT PK_PackageIncludedService PRIMARY KEY (PackageID, ServiceID)" +
                "); END");
        } catch (Exception exception) {
            logger.error("Unable to create dbo.PackageIncludedService", exception);
        }

        // 10. Subscription table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Subscription') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Subscription') AND name = 'GateAccessPin') ALTER TABLE Subscription ADD GateAccessPin VARCHAR(50) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Subscription') AND name = 'LoyaltyPointsEarned') ALTER TABLE Subscription ADD LoyaltyPointsEarned INT NULL DEFAULT 50; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Subscription') AND name = 'PackageNameSnap') ALTER TABLE Subscription ADD PackageNameSnap VARCHAR(100) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Subscription') AND name = 'PricePaidSnap') ALTER TABLE Subscription ADD PricePaidSnap DECIMAL(10,2) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Subscription') AND name = 'VisitsRemaining') ALTER TABLE Subscription ADD VisitsRemaining INT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Subscription') AND name = 'CreatedAt') ALTER TABLE Subscription ADD CreatedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(); " +
                "END");
        } catch (Exception ignored) {}

        synchronizeSubscriptionStatusConstraint();

        // 11. ParkingFacility table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('ParkingFacility') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingFacility') AND name = 'Address') ALTER TABLE ParkingFacility ADD Address NVARCHAR(255) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingFacility') AND name = 'TotalFloors') ALTER TABLE ParkingFacility ADD TotalFloors INT NOT NULL DEFAULT 1; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingFacility') AND name = 'Status') ALTER TABLE ParkingFacility ADD [Status] VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingFacility') AND name = 'Description') ALTER TABLE ParkingFacility ADD Description NVARCHAR(MAX) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ParkingFacility') AND name = 'ImageUrl') ALTER TABLE ParkingFacility ADD ImageUrl VARCHAR(500) NULL; " +
                "UPDATE ParkingFacility SET ContactNumber = '+94' + SUBSTRING(REPLACE(REPLACE(ContactNumber, ' ', ''), '-', ''), 2, 20) WHERE ContactNumber LIKE '0%' AND ContactNumber NOT LIKE '+94%'; " +
                "UPDATE ParkingFacility SET ContactNumber = '+94112345678' WHERE ContactNumber IS NULL OR ContactNumber NOT LIKE '+94%'; " +
                "UPDATE ParkingFacility SET Address = 'Colombo Main City, Sri Lanka' WHERE Address IS NULL OR DATALENGTH(Address) < 20; " +
                "UPDATE ParkingFacility SET Description = 'Automated multi-level parking facility with 24/7 security and smart bay monitoring.' WHERE Description IS NULL OR DATALENGTH(Description) < 20; " +
                "UPDATE ParkingFacility SET TotalFloors = 1 WHERE TotalFloors IS NULL OR TotalFloors < 1 OR TotalFloors > 10; " +
                "END");
        } catch (Exception ignored) {}

        // 12. Feedback table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Feedback') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Feedback') AND name = 'adminresponse') ALTER TABLE Feedback ADD adminresponse NVARCHAR(MAX) NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Feedback') AND name = 'respondedat') ALTER TABLE Feedback ADD respondedat DATETIME2 NULL; " +
                "IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Fb_Status') ALTER TABLE Feedback DROP CONSTRAINT CK_Fb_Status; " +
                "ALTER TABLE Feedback ADD CONSTRAINT CK_Fb_Status CHECK ([Status] IN ('PENDING', 'SUBMITTED', 'REVIEWED', 'RESPONDED', 'RESOLVED', 'ARCHIVED')); " +
                "END");
        } catch (Exception ignored) {}

        // 13. Notification table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('Notification') IS NOT NULL BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Notification') AND name = 'ReferenceID') ALTER TABLE Notification ADD ReferenceID BIGINT NULL; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Notification') AND name = 'IsRead') ALTER TABLE Notification ADD IsRead BIT NOT NULL DEFAULT 0; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Notification') AND name = 'DeliveryStatus') ALTER TABLE Notification ADD DeliveryStatus VARCHAR(20) NULL DEFAULT 'PENDING'; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Notification') AND name = 'SentAt') ALTER TABLE Notification ADD SentAt DATETIME2 NULL; " +
                "END");
        } catch (Exception ignored) {}

        // 14. OnlineReservation table creation
        try {
            jdbcTemplate.execute("IF OBJECT_ID('OnlineReservation') IS NULL BEGIN " +
                "CREATE TABLE OnlineReservation (" +
                "ReservationID BIGINT NOT NULL, " +
                "BookingSource VARCHAR(50) NULL, " +
                "AdvanceBookingRef VARCHAR(60) NULL, " +
                "CONSTRAINT PK_OnlineReservation PRIMARY KEY (ReservationID)" +
                "); END");
        } catch (Exception ignored) {}

        // 15. WalkInReservation table creation
        try {
            jdbcTemplate.execute("IF OBJECT_ID('WalkInReservation') IS NULL BEGIN " +
                "CREATE TABLE WalkInReservation (" +
                "ReservationID BIGINT NOT NULL, " +
                "GuestVehicleRegNumber VARCHAR(30) NULL, " +
                "GuestContactNumber VARCHAR(20) NULL, " +
                "CONSTRAINT PK_WalkInReservation PRIMARY KEY (ReservationID)" +
                "); END");
        } catch (Exception ignored) {}

        // 16. CheckoutQuote table creation
        try {
            jdbcTemplate.execute("IF OBJECT_ID('CheckoutQuote') IS NULL BEGIN " +
                "CREATE TABLE CheckoutQuote (" +
                "QuoteID BIGINT IDENTITY(1,1) NOT NULL, " +
                "CustomerID BIGINT NOT NULL, " +
                "OriginalAmount DECIMAL(18,2) NOT NULL, " +
                "FinalAmount DECIMAL(18,2) NOT NULL, " +
                "AppliedSubscriptionID BIGINT NULL, " +
                "AppliedDiscountID BIGINT NULL, " +
                "LoyaltyApplied BIT NOT NULL DEFAULT 0, " +
                "LoyaltyAmount DECIMAL(18,2) NOT NULL DEFAULT 0, " +
                "ConsumedByOrderReferenceID BIGINT NULL, " +
                "RolledBack BIT NOT NULL DEFAULT 0, " +
                "CreatedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "ExpiresAt DATETIME2 NOT NULL, " +
                "[Status] VARCHAR(20) NOT NULL DEFAULT 'OPEN', " +
                "CONSTRAINT PK_CheckoutQuote PRIMARY KEY (QuoteID)" +
                "); END");
        } catch (Exception ignored) {}

        // 17. ReservationServiceItem table schema synchronization
        try {
            jdbcTemplate.execute("IF OBJECT_ID('ReservationServiceItem') IS NULL BEGIN " +
                "CREATE TABLE ReservationServiceItem (" +
                "ServiceItemID BIGINT IDENTITY(1,1) NOT NULL, " +
                "ReservationID BIGINT NOT NULL, " +
                "ServiceID BIGINT NOT NULL, " +
                "ServicePrice DECIMAL(10,2) NOT NULL DEFAULT 0, " +
                "Notes NVARCHAR(200) NULL, " +
                "CONSTRAINT PK_ReservationServiceItem PRIMARY KEY (ServiceItemID)" +
                "); END ELSE BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ReservationServiceItem') AND name = 'ServiceItemID') ALTER TABLE ReservationServiceItem ADD ServiceItemID BIGINT IDENTITY(1,1); " +
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('ReservationServiceItem') AND name = 'Notes') ALTER TABLE ReservationServiceItem ADD Notes NVARCHAR(200) NULL; " +
                "END");
        } catch (Exception ignored) {}

        // 18. DiscountRedemption table creation
        try {
            jdbcTemplate.execute("IF OBJECT_ID('DiscountRedemption') IS NULL BEGIN " +
                "CREATE TABLE DiscountRedemption (" +
                "RedemptionID BIGINT IDENTITY(1,1) NOT NULL, " +
                "DiscountID BIGINT NOT NULL, " +
                "CustomerID BIGINT NOT NULL, " +
                "QuoteID BIGINT NOT NULL, " +
                "OrderReferenceID BIGINT NULL, " +
                "[Status] VARCHAR(20) NOT NULL DEFAULT 'HELD', " +
                "RedeemedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "CONSTRAINT PK_DiscountRedemption PRIMARY KEY (RedemptionID)" +
                "); END");
        } catch (Exception ignored) {}

        // 19. LoyaltyStatus table creation
        try {
            jdbcTemplate.execute("IF OBJECT_ID('LoyaltyStatus') IS NULL BEGIN " +
                "CREATE TABLE LoyaltyStatus (" +
                "CustomerID BIGINT NOT NULL, " +
                "CompletedReservationCount INT NOT NULL DEFAULT 0, " +
                "[Tier] VARCHAR(20) NOT NULL DEFAULT 'STANDARD', " +
                "CONSTRAINT PK_LoyaltyStatus PRIMARY KEY (CustomerID)" +
                "); END");
        } catch (Exception ignored) {}
    }

    @Override
    public void run(String... args) {
        initDatabaseSchema();

        Role customerRole = ensureRole("CUSTOMER", "Regular customer who makes parking reservations");
        Role staffRole = ensureRole("STAFF", "Parking facility staff");
        Role adminRole = ensureRole("ADMIN", "System administrator");
        normalizeLegacyStaffRoles(staffRole);

        // Seed default Admin members if not present or synchronize their credentials
        ensureAdminAccount("admin@example.com", "System Administrator", adminRole);
        ensureAdminAccount("adminexample@gmail.com", "Administrator", adminRole);
        ensureAdminAccount("it25101414@my.sliit.lk", "System Administrator", adminRole);

        // Seed default Customer if not present
        if (!userEmailExists("customer@example.com")) {
            Customer customer = new Customer();
            customer.setFullName("John Doe");
            customer.setEmail("customer@example.com");
            customer.setPhone("+94772000000");
            customer.setPassword(passwordEncoder.encode("customer123"));
            customer.setRole(customerRole);
            customer.setStatus(User.UserStatus.ACTIVE);
            customerRepository.save(customer);
        } else {
            try {
                jdbcTemplate.update(
                    "UPDATE dbo.[User] SET [Password] = ? WHERE Email = 'customer@example.com'",
                    passwordEncoder.encode("customer123")
                );
            } catch (Exception ignored) {}
        }

        // Seed default Staff if not present
        if (!userEmailExists("staff@example.com")) {
            Staff staff = new Staff();
            staff.setFullName("Staff Operator");
            staff.setEmail("staff@example.com");
            staff.setPhone("+94773000000");
            staff.setPassword(passwordEncoder.encode("staff123"));
            staff.setRole(staffRole);
            staff.setStatus(User.UserStatus.ACTIVE);
            staff.setEmployeeId("EMP-001");
            staff.setShift("DAY");
            staffRepository.save(staff);
        } else {
            try {
                jdbcTemplate.update(
                    "UPDATE dbo.[User] SET [Password] = ? WHERE Email = 'staff@example.com'",
                    passwordEncoder.encode("staff123")
                );
            } catch (Exception ignored) {}
        }
        normalizeStaffProfiles();
        if (facilityStaffAccountService != null) {
            facilityStaffAccountService.ensureAccountsForAllFacilities();
        }
        assignUnassignedStaffWhenThereIsOneFacility();
        ensureReservableParkingSpaces();
    }

    private void ensureReservableParkingSpaces() {
        for (ParkingFacility facility : parkingFacilityRepository.findByStatus(ParkingFacility.FacilityStatus.ACTIVE)) {
            java.util.List<ParkingSpace> existingSpaces = parkingSpaceRepository.findByFacilityId(facility.getId());
            int declaredCapacity = facility.getTotalCapacity() != null ? facility.getTotalCapacity() : 24;
            int targetSpaceCount = Math.min(24, Math.max(1, declaredCapacity));
            int spacesToCreate = targetSpaceCount - existingSpaces.size();
            if (spacesToCreate <= 0) {
                continue;
            }

            java.util.Set<String> existingCodes = existingSpaces.stream()
                    .map(ParkingSpace::getSpaceNumber)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            java.util.List<ParkingSpace> newSpaces = new java.util.ArrayList<>(spacesToCreate);
            int floorCount = Math.max(1, Math.min(10, facility.getTotalFloors()));
            int ordinal = 1;

            while (newSpaces.size() < spacesToCreate) {
                int floor = ((ordinal - 1) % floorCount) + 1;
                String zoneCode = ((ordinal - 1) / floorCount) % 2 == 0 ? "A" : "B";
                int sequence = ((ordinal - 1) / (floorCount * 2)) + 1;
                String spaceCode = String.format("F%d-L%d%s-%02d", facility.getId(), floor, zoneCode, sequence);
                ordinal++;
                if (!existingCodes.add(spaceCode)) {
                    continue;
                }

                ParkingSpace space = new ParkingSpace();
                space.setSpaceNumber(spaceCode);
                space.setFloor(floor);
                space.setZone("ZONE-" + zoneCode);
                space.setParkingType("STANDARD");
                space.setStatus(ParkingSpace.ParkingSpaceStatus.AVAILABLE);
                space.setHourlyRate(java.math.BigDecimal.valueOf(250));
                space.setHasEVCharging(false);
                space.setIsAccessible(false);
                space.setAllocationType("GENERAL");
                space.setFacility(facility);
                newSpaces.add(space);
            }

            parkingSpaceRepository.saveAll(newSpaces);
            logger.info("Added {} reservable parking spaces to {}.", newSpaces.size(), facility.getName());
        }
    }

    private void normalizeStaffProfiles() {
        if (jdbcTemplate == null) return;
        try {
            jdbcTemplate.execute(
                    "IF OBJECT_ID(N'dbo.Staff', N'U') IS NOT NULL " +
                    "BEGIN " +
                    "INSERT INTO dbo.Staff (UserID, EmployeeID, Shift, FacilityID) " +
                    "SELECT u.UserID, CONCAT('AUTO-', u.UserID), 'DAY', NULL " +
                    "FROM dbo.[User] u " +
                    "INNER JOIN dbo.Role r ON r.RoleID = u.RoleID " +
                    "WHERE UPPER(LTRIM(RTRIM(r.RoleName))) IN ('STAFF', 'SUPERVISOR', 'GATE_OPERATOR') " +
                    "AND NOT EXISTS (SELECT 1 FROM dbo.Staff s WHERE s.UserID = u.UserID); " +
                    "END");
        } catch (Exception e) {
            logger.warn("Could not normalize staff profile rows: {}", e.getMessage());
        }
    }

    void assignUnassignedStaffWhenThereIsOneFacility() {
        try {
            java.util.List<ParkingFacility> activeFacilities = parkingFacilityRepository.findAll().stream()
                    .filter(f -> f.getStatus() == ParkingFacility.FacilityStatus.ACTIVE)
                    .toList();
            if (activeFacilities.size() != 1) {
                if (activeFacilities.isEmpty()) {
                    logger.info("Skipped automatic staff facility assignment because there are no active facilities.");
                } else {
                    logger.info("Skipped automatic staff facility assignment because {} active facilities require an explicit admin selection.",
                            activeFacilities.size());
                }
                return;
            }
            ParkingFacility facility = activeFacilities.get(0);
            for (Staff staff : staffRepository.findAll()) {
                if (staff.getAssignedFacility() == null) {
                    staff.setAssignedFacility(facility);
                    staffRepository.save(staff);
                    logger.info("Assigned staff {} to facility {} because it was the only active facility.",
                            staff.getEmail(), facility.getName());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not normalize staff facility assignments: {}", e.getMessage());
        }
    }

    private void ensureAdminAccount(String email, String fullName, Role adminRole) {
        if (userEmailExists(email)) {
            try {
                jdbcTemplate.update(
                    "UPDATE dbo.[User] SET RoleID = ?, [Status] = 'ACTIVE', [Password] = ? WHERE Email = ?",
                    adminRole.getId(), passwordEncoder.encode("admin123"), email
                );
            } catch (Exception e) {
                logger.warn("Could not synchronize admin credentials for {}: {}", email, e.getMessage());
            }
            return;
        }

        Admin admin = new Admin();
        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setPhone("+94771000000");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole(adminRole);
        admin.setStatus(User.UserStatus.ACTIVE);
        adminRepository.save(admin);
    }

    /** Older databases reject PENDING before the membership payment can be created. */
    void synchronizeSubscriptionStatusConstraint() {
        if (jdbcTemplate == null) return;
        String allowedStatuses = java.util.Arrays.stream(com.example.parkngo.subscription.Subscription.SubscriptionStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(java.util.stream.Collectors.joining(", "));
        try {
            jdbcTemplate.execute("""
                    IF OBJECT_ID(N'dbo.Subscription', N'U') IS NOT NULL
                    BEGIN
                      BEGIN TRY
                        BEGIN TRANSACTION;
                        DECLARE @statusColumn INT = COLUMNPROPERTY(OBJECT_ID(N'dbo.Subscription'), N'Status', 'ColumnId');
                        DECLARE @dropChecks NVARCHAR(MAX) = N'';
                        SELECT @dropChecks = @dropChecks + N'ALTER TABLE dbo.Subscription DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
                        FROM sys.check_constraints cc
                        WHERE cc.parent_object_id = OBJECT_ID(N'dbo.Subscription')
                          AND (cc.parent_column_id = @statusColumn OR (
                            EXISTS (SELECT 1 FROM sys.sql_expression_dependencies d
                                    WHERE d.referencing_id = cc.object_id
                                      AND d.referenced_id = cc.parent_object_id AND d.referenced_minor_id = @statusColumn)
                            AND NOT EXISTS (SELECT 1 FROM sys.sql_expression_dependencies d
                                            WHERE d.referencing_id = cc.object_id
                                              AND d.referenced_id = cc.parent_object_id
                                              AND d.referenced_minor_id NOT IN (0, @statusColumn))));
                        IF LEN(@dropChecks) > 0 EXEC sys.sp_executesql @dropChecks;
                        ALTER TABLE dbo.Subscription WITH CHECK ADD CONSTRAINT CK_Subscription_Status
                          CHECK ([Status] IN (%s));
                        COMMIT TRANSACTION;
                      END TRY
                      BEGIN CATCH
                        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
                        THROW;
                      END CATCH
                    END
                    """.formatted(allowedStatuses));
        } catch (Exception exception) {
            logger.error("Unable to synchronize dbo.Subscription.Status check constraint", exception);
            throw new IllegalStateException("Database schema migration failed for dbo.Subscription.Status", exception);
        }
    }

    private boolean userEmailExists(String email) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.[User] WHERE Email = ?",
            Integer.class,
            email
        );
        return count != null && count > 0;
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            return roleRepository.save(role);
        });
    }

    private void normalizeLegacyStaffRoles(Role staffRole) {
        jdbcTemplate.update(
                "UPDATE dbo.[User] SET RoleID = ? WHERE RoleID IN " +
                "(SELECT RoleID FROM dbo.Role WHERE RoleName IN ('SUPERVISOR', 'GATE_OPERATOR'))",
                staffRole.getId());
        jdbcTemplate.update(
                "DELETE FROM dbo.Role WHERE RoleName IN ('SUPERVISOR', 'GATE_OPERATOR')");
    }
}
