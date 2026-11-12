# ParkNGo - Complete Setup Guide

## Prerequisites

Before starting, ensure you have the following installed:

### System Requirements
- **Operating System**: Windows, macOS, or Linux
- **Disk Space**: At least 1 GB for dependencies and database
- **RAM**: Minimum 4 GB (8 GB recommended)

### Required Software

1. **Java Development Kit (JDK) 21 or higher**
   ```bash
   java -version
   ```
   Download from: https://www.oracle.com/java/technologies/downloads/

2. **Maven 3.6 or higher**
   ```bash
   mvn -version
   ```
   Download from: https://maven.apache.org/download.cgi

3. **Microsoft SQL Server (2019, 2022, Express, or Docker)**
   ```bash
   sqlcmd -?
   ```
   Download from: https://www.microsoft.com/en-us/sql-server/sql-server-downloads

4. **Git** (optional, for version control)
   ```bash
   git --version
   ```

## Installation Steps

### Step 1: Install Java JDK 21

**Windows:**
1. Download JDK 21 from Oracle website
2. Run the installer
3. Follow the installation wizard
4. Set JAVA_HOME environment variable:
   - Right-click "This PC" → Properties → Advanced system settings
   - Click "Environment Variables"
   - Click "New" under System variables
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Java\jdk-17.x.x`

**macOS (using Homebrew):**
```bash
brew install openjdk@21
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get update
sudo apt-get install openjdk-21-jdk
```

### Step 2: Install Maven

**Windows:**
1. Download Maven binary from apache.org
2. Extract to a folder (e.g., `C:\Program Files\apache-maven-3.x.x`)
3. Add to PATH environment variable

**macOS (using Homebrew):**
```bash
brew install maven
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get install maven
```

Verify installation:
```bash
mvn --version
```

### Step 3: Install & Start Microsoft SQL Server

**Option A: Using Docker (Recommended for macOS/Linux):**
```bash
docker run -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=<choose-a-strong-password>" \
   -p 1433:1433 --name mssql -d \
   mcr.microsoft.com/mssql/server:2022-latest
```

**Option B: Windows (Native Installer / SQL Server Express):**
1. Download SQL Server 2022 Express or Developer Edition from Microsoft website.
2. Install with SQL Server and Windows Authentication mode (Mixed Mode).
3. Set the `SA` password to a strong value.
4. Ensure TCP/IP protocol is enabled in SQL Server Configuration Manager on port 1433.

### Step 4: Create the Database

Connect to SQL Server using Azure Data Studio, SSMS (SQL Server Management Studio), or `sqlcmd`, then run:

```sql
CREATE DATABASE parkngo;
GO
```

*(Note: In SQL Server, the database must be created explicitly before running the application.)*

### Step 5: Clone or Copy the Project

If using Git:
```bash
git clone <repository-url>
cd ParkNGo
```

Or if you already have the project folder, navigate to it:
```bash
cd /path/to/ParkNGo
```

### Step 6: Configure Database Connection

The application is configured with environment-variable placeholders in `src/main/resources/application.properties`:

```properties
# Database Configuration (MSSQL / Microsoft SQL Server)
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=parkngo;encrypt=true;trustServerCertificate=true;
spring.datasource.username=${DB_USERNAME:SA}
spring.datasource.password=${DB_PASSWORD}

# JPA/Hibernate Configuration
spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect
spring.jpa.hibernate.ddl-auto=none
```

> **Note on DDL Configuration:** `spring.jpa.hibernate.ddl-auto` is set to `none` to prevent Hibernate from conflicting with SQL Server check constraints and discriminator column definitions. The application uses idempotent SQL migration scripts plus `DataInitializer` for schema synchronization and demo-data seeding.

Set `DB_PASSWORD` (and optionally `MAIL_APP_PASSWORD` for email notifications) before starting the application:

```bash
# macOS / Linux
export DB_USERNAME=SA
export DB_PASSWORD=your-sql-server-password
export MAIL_APP_PASSWORD=your-gmail-app-password
```

```powershell
# Windows PowerShell
$env:DB_USERNAME = "SA"
$env:DB_PASSWORD = "your-sql-server-password"
$env:MAIL_APP_PASSWORD = "your-gmail-app-password"
```

Never commit sensitive passwords or email credentials directly to `application.properties`.

### Step 7: Build the Project

```bash
mvn clean install
```

This will:
- Download all dependencies (may take a few minutes)
- Compile the Java source code
- Run tests
- Package the application

If you want to skip tests during build:
```bash
mvn clean install -DskipTests
```

### Step 8: Run the Application

```bash
mvn spring-boot:run
```

The application will start and you should see:
```
2026-08-20 12:00:00 - Started ParkNGoApplication in X.XXX seconds
```

Open your browser and navigate to:
```
http://localhost:8080
```

## First-Time Login

Create an account through the registration page, or have an administrator create the required staff and admin accounts.

## Troubleshooting

### Issue: "Maven command not found"
**Solution:** Maven is not installed or not in PATH
- Install Maven (see Step 2)
- On Windows, restart your terminal after adding to PATH
- On macOS/Linux, add to ~/.bash_profile or ~/.zshrc:
  ```bash
  export PATH=$PATH:/usr/local/bin/maven
  ```

### Issue: "Port 8080 is already in use"
**Solution:** Another application is using port 8080
- Option 1: Stop the other application
- Option 2: Change Spring Boot port in application.properties:
  ```properties
  server.port=8081
  ```

### Issue: "Cannot connect to database"
**Check:**
1. SQL Server is running: check your Docker container (`docker ps`) or Windows SQL Server Service
2. Database exists: run `SELECT name FROM sys.databases WHERE name = 'parkngo';` in `sqlcmd` or SSMS / Azure Data Studio
3. `DB_PASSWORD` is set and `DB_USERNAME` (if supplied) is correct
4. Port 1433 is open and accessible (check TCP/IP enabled in SQL Server Configuration Manager)

### Issue: Build fails with compilation errors
**Solutions:**
1. Ensure JDK 21 is installed: `java -version`
2. Clear Maven cache: `mvn clean`
3. Delete target folder: `rm -rf target/`
4. Rebuild: `mvn clean install`

### Issue: "404 Template not found" when accessing pages
**Solution:** This is normal for some placeholder pages. The core functionality pages are fully implemented. You can extend placeholder templates as needed.

## Project Structure Reference

```
ParkNGo/
├── pom.xml                          # Maven configuration
├── README.md                         # Project documentation
├── SETUP.md                          # This file
├── .gitignore                        # Git configuration
│
├── src/main/
│   ├── java/com/example/parkngo/
│   │   ├── ParkNGoApplication.java
│   │   ├── vehicle/                 # Vehicle Management (Entity, Repo, Service, Controller)
│   │   ├── space/                   # Parking Space Management (Entity, Repo, Service, Controller)
│   │   ├── reservation/             # Reservation Management (Entity, Repo, Service, Controller)
│   │   ├── payment/                 # Payment Management (Entity, Repo, Service, Controller)
│   │   ├── discount/                # Package & Discount Management (Entities, Repos, Services, Controller)
│   │   ├── facility/                # Amenities & Facility Management (Entities, Repos, Services, Controller)
│   │   ├── feedback/                # Feedback & Support Management (Entities, Repos, Services, Controller)
│   │   ├── user/                    # User & Access Management (Entities, Repos, Service, Controllers)
│   │   ├── dashboard/               # Role Dashboards & Reports
│   │   ├── config/                  # Spring Configuration & DataInitializer
│   │   ├── security/                # Security Configuration
│   │   └── util/                    # Utility Classes
│   │
│   └── resources/
│       ├── application.properties    # Application configuration
│       └── templates/               # Thymeleaf HTML templates
│           ├── fragments/           # Reusable template fragments (Tailwind/FontAwesome CDN)
│           ├── auth/                # Authentication pages
│           ├── customer/            # Customer pages
│           ├── staff/               # Staff pages
│           └── admin/               # Admin pages
│
└── src/test/
    └── java/com/example/parkngo/    # Unit tests by feature package
```

## Key Files

- **pom.xml**: All dependencies and build configuration
- **application.properties**: Database and server configuration
- **SecurityConfig.java**: Spring Security setup
- **DataInitializer.java**: Creates demo data on startup
- **ReservationService.java**: Core business logic for reservations

## Development Workflow

### Making Changes

1. Edit Java files in `src/main/java/`
2. Edit templates in `src/main/resources/templates/`
3. Save changes
4. Spring Boot DevTools will auto-reload (if running with `mvn spring-boot:run`)
5. Refresh browser to see changes

### Adding Features

1. Create entity in `entity/` package
2. Create repository in `repository/` package
3. Create service in `service/` package
4. Create controller method in appropriate controller
5. Create Thymeleaf template
6. Write tests for business logic

### Testing

Run all tests:
```bash
mvn test
```

Run specific test class:
```bash
mvn test -Dtest=ReservationServiceTest
```

## Useful Commands

```bash
# Clean and build
mvn clean install

# Run application
mvn spring-boot:run

# Run tests
mvn test

# Create packaged JAR
mvn clean package

# Check dependencies
mvn dependency:tree

# Format code (with proper plugin)
mvn com.spotify.fmt:fmt-maven-plugin:format

# View running processes (find port 8080)
lsof -i :8080

# Kill process on specific port
kill -9 $(lsof -t -i :8080)
```

## Support Resources

- **Spring Boot Documentation**: https://spring.io/projects/spring-boot
- **Spring Data JPA**: https://spring.io/projects/spring-data-jpa
- **Thymeleaf**: https://www.thymeleaf.org/
- **Tailwind CSS**: https://tailwindcss.com/
- **Microsoft SQL Server**: https://learn.microsoft.com/en-us/sql/

## Next Steps

1. Run the application
2. Login with demo credentials
3. Explore the customer dashboard
4. Create a reservation
5. Test different user roles
6. Review the codebase structure
7. Extend templates and features as needed

---

Happy parking! 🚗
