-- Run this script once in SSMS before deploying the staff-facility feature.
-- It is idempotent and does not modify existing Staff records or history.
IF COL_LENGTH('dbo.Staff', 'FacilityID') IS NULL
BEGIN
    ALTER TABLE dbo.Staff ADD FacilityID INT NULL;
END;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Staff_ParkingFacility')
BEGIN
    ALTER TABLE dbo.Staff WITH CHECK
        ADD CONSTRAINT FK_Staff_ParkingFacility
        FOREIGN KEY (FacilityID) REFERENCES dbo.ParkingFacility(FacilityID);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Staff_FacilityID' AND object_id = OBJECT_ID('dbo.Staff'))
BEGIN
    CREATE INDEX IX_Staff_FacilityID ON dbo.Staff(FacilityID);
END;
