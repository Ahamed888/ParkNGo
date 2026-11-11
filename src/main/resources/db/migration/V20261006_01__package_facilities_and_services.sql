-- Run once in SQL Server before using package availability by complex.
-- Existing packages remain available at all complexes.
IF COL_LENGTH('dbo.[Package]', 'AllFacilities') IS NULL
BEGIN
    ALTER TABLE dbo.[Package] ADD AllFacilities BIT NOT NULL CONSTRAINT DF_Package_AllFacilities DEFAULT 1;
END;

IF OBJECT_ID('dbo.PackageFacility', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.PackageFacility (
        PackageID INT NOT NULL,
        FacilityID INT NOT NULL,
        CONSTRAINT PK_PackageFacility PRIMARY KEY (PackageID, FacilityID),
        CONSTRAINT FK_PackageFacility_Package FOREIGN KEY (PackageID) REFERENCES dbo.[Package](PackageID),
        CONSTRAINT FK_PackageFacility_Facility FOREIGN KEY (FacilityID) REFERENCES dbo.ParkingFacility(FacilityID)
    );
END;

IF OBJECT_ID('dbo.PackageIncludedService', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.PackageIncludedService (
        PackageID INT NOT NULL,
        ServiceID INT NOT NULL,
        CONSTRAINT PK_PackageIncludedService PRIMARY KEY (PackageID, ServiceID),
        CONSTRAINT FK_PackageIncludedService_Package FOREIGN KEY (PackageID) REFERENCES dbo.[Package](PackageID),
        CONSTRAINT FK_PackageIncludedService_Service FOREIGN KEY (ServiceID) REFERENCES dbo.FacilityService(ServiceID)
    );
END;
