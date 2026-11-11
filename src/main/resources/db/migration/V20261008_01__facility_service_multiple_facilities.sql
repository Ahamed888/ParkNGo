-- Convert FacilityService's single FacilityID association to a many-to-many join table.
-- Existing assignments are copied before the legacy column is removed.
IF OBJECT_ID('dbo.FacilityServiceFacility', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.FacilityServiceFacility (
        ServiceID INT NOT NULL,
        FacilityID INT NOT NULL,
        CONSTRAINT PK_FacilityServiceFacility PRIMARY KEY (ServiceID, FacilityID),
        CONSTRAINT FK_FacilityServiceFacility_Service FOREIGN KEY (ServiceID) REFERENCES dbo.FacilityService(ServiceID),
        CONSTRAINT FK_FacilityServiceFacility_Facility FOREIGN KEY (FacilityID) REFERENCES dbo.ParkingFacility(FacilityID)
    );
END;

IF COL_LENGTH('dbo.FacilityService', 'FacilityID') IS NOT NULL
BEGIN
    INSERT INTO dbo.FacilityServiceFacility (ServiceID, FacilityID)
    SELECT fs.ServiceID, fs.FacilityID
    FROM dbo.FacilityService fs
    WHERE fs.FacilityID IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.FacilityServiceFacility fsf
          WHERE fsf.ServiceID = fs.ServiceID
            AND fsf.FacilityID = fs.FacilityID
      );

    DECLARE @facilityFk sysname;
    SELECT @facilityFk = fk.name
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
    JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
    WHERE fk.parent_object_id = OBJECT_ID('dbo.FacilityService') AND c.name = 'FacilityID';

    IF @facilityFk IS NOT NULL
        EXEC('ALTER TABLE dbo.FacilityService DROP CONSTRAINT [' + @facilityFk + ']');

    ALTER TABLE dbo.FacilityService DROP COLUMN FacilityID;
END;
