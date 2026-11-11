-- IDs use BIGINT to match the Long entity IDs and existing parent tables.
IF OBJECT_ID('dbo.MembershipBenefit', 'U') IS NULL
BEGIN
CREATE TABLE dbo.MembershipBenefit (
 BenefitID BIGINT IDENTITY(1,1) PRIMARY KEY, PackageID BIGINT NOT NULL, BenefitType VARCHAR(50) NOT NULL,
 ValueAmount DECIMAL(12,2) NULL, QualifyingCount INT NULL, AllowanceMinutes INT NULL,
 RewardDescription NVARCHAR(500) NULL, Frequency VARCHAR(20) NOT NULL CONSTRAINT DF_MembershipBenefit_Frequency DEFAULT 'UNLIMITED', ServiceID BIGINT NULL,
 CONSTRAINT FK_MembershipBenefit_Package FOREIGN KEY (PackageID) REFERENCES dbo.[Package](PackageID),
 CONSTRAINT FK_MembershipBenefit_Service FOREIGN KEY (ServiceID) REFERENCES dbo.FacilityService(ServiceID)
)
END;
IF OBJECT_ID('dbo.MembershipBenefitUsage', 'U') IS NULL
BEGIN
CREATE TABLE dbo.MembershipBenefitUsage (
 UsageID BIGINT IDENTITY(1,1) PRIMARY KEY, SubscriptionID BIGINT NOT NULL, BenefitID BIGINT NOT NULL, PeriodStart DATE NOT NULL, UnitsUsed INT NOT NULL CONSTRAINT DF_MembershipBenefitUsage_Units DEFAULT 0,
 CONSTRAINT UQ_MembershipBenefitUsage UNIQUE (SubscriptionID, BenefitID, PeriodStart),
 CONSTRAINT FK_MembershipBenefitUsage_Subscription FOREIGN KEY (SubscriptionID) REFERENCES dbo.Subscription(SubscriptionID),
 CONSTRAINT FK_MembershipBenefitUsage_Benefit FOREIGN KEY (BenefitID) REFERENCES dbo.MembershipBenefit(BenefitID)
)
END;
