-- Minimal one-time password reset token storage for SQL Server.
IF OBJECT_ID('dbo.PasswordResetToken', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.PasswordResetToken (
        TokenID BIGINT IDENTITY(1,1) NOT NULL,
        UserID BIGINT NOT NULL,
        TokenHash CHAR(64) NOT NULL,
        ExpiresAt DATETIME2 NOT NULL,
        UsedAt DATETIME2 NULL,
        CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_PasswordResetToken_CreatedAt DEFAULT SYSDATETIME(),
        CONSTRAINT PK_PasswordResetToken PRIMARY KEY (TokenID),
        CONSTRAINT UQ_PasswordResetToken_TokenHash UNIQUE (TokenHash),
        CONSTRAINT FK_PasswordResetToken_User FOREIGN KEY (UserID)
            REFERENCES dbo.[User](UserID) ON DELETE CASCADE
    );

    CREATE INDEX IX_PasswordResetToken_UserID
        ON dbo.PasswordResetToken(UserID);
END;
