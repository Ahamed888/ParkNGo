-- Run in SSMS with an account allowed to create databases.
-- This script creates only the dedicated test database; it never changes
-- the normal ParkNGo database.
IF DB_ID(N'parkngo_test') IS NULL
BEGIN
    CREATE DATABASE [parkngo_test];
END;
