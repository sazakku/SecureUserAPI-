# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SecureUserAPI is a Spring Boot 3.4.4 / Java 21 REST API for user and role management with JWT authentication. It uses PostgreSQL for persistence, Spring Security for auth, and Spring Data JPA/Hibernate for ORM. The main package is `com.secureuser.secureuserapi`.

## Commands

```bash
# Run the application
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=SecureuserapiApplicationTests

# Run a single test method
./mvnw test -Dtest=ClassName#methodName
```

## Database Setup

Requires a running PostgreSQL instance. Configure credentials in `src/main/resources/application.properties` (the README references `application.yml` but the actual file is `.properties`):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/secureuser
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

Create the database before running:
```sql
CREATE DATABASE secureuser;
```

## Planned Architecture

The application is in early scaffolding — only the entry point exists. The intended package layout under `com.secureuser.secureuserapi` is:

- `controller` — REST controllers (user registration, login, role management)
- `dto` — Request/response DTOs (keep entities out of API layer)
- `model` — JPA entities (`User`, `Role`)
- `repository` — Spring Data JPA repositories
- `service` — Business logic layer
- `security` — JWT filter, token provider, `SecurityConfig`, `UserDetailsService` impl
- `config` — General Spring configuration beans

## Key Technology Notes

- **JWT**: Not yet added to `pom.xml` — will need a JWT library (e.g., `io.jsonwebtoken:jjwt` or `com.auth0:java-jwt`) when implementing the security layer.
- **Lombok**: Used for boilerplate reduction (`@Data`, `@Builder`, etc.). Annotation processing is configured in the Maven compiler plugin.
- **Validation**: `spring-boot-starter-validation` is included — use `@Valid` on controller method parameters and Bean Validation annotations on DTOs.
- **Password encoding**: BCrypt via Spring Security's `PasswordEncoder`.
- **devtools**: Included for hot reload during development.
