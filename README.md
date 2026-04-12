# SecureUserAPI

A secure REST API for user and role management built with Spring Boot 3, Java 21, and JWT authentication. Designed following **hexagonal architecture** (ports and adapters) with TDD and RBAC.

## Prerequisites

Before running this project, ensure you have the following installed:

| Requirement | Version   | Notes                                     |
|-------------|-----------|-------------------------------------------|
| Java        | 21+       | LTS release required                      |
| Maven       | 3.9+      | Or use the included `./mvnw` wrapper      |
| PostgreSQL  | 14+       | Must be running before starting the app   |

## Tech Stack

| Layer          | Technology                                          |
|----------------|-----------------------------------------------------|
| Language       | Java 21                                             |
| Framework      | Spring Boot 3.4.4                                   |
| Security       | Spring Security + JJWT 0.12.6 (`io.jsonwebtoken`) |
| Persistence    | Spring Data JPA / Hibernate + PostgreSQL            |
| Validation     | Bean Validation (`spring-boot-starter-validation`)  |
| Build          | Maven                                               |
| Testing        | JUnit 5 + Mockito + `@WebMvcTest`                  |
| Utilities      | Lombok                                              |

## Features

- User registration with automatic `ROLE_USER` assignment
- JWT-based authentication (login returns a signed Bearer token)
- Role-based access control (RBAC) with `ROLE_USER` and `ROLE_ADMIN`
- JWT validation: signature, expiration, and issuer enforced on every protected request
- BCrypt password encoding (cost factor 10)
- Java records as DTOs — immutable and validated
- Bean Validation on all inputs (`@Valid`, `@NotBlank`, `@Email`, `@Pattern`, etc.)
- Global exception handler (`@RestControllerAdvice`) covering 400/401/403/404/409/500
- Consistent response envelope for success and error responses
- Automatic role seeding (`ROLE_USER`, `ROLE_ADMIN`) on startup via `DataInitializer`
- Hexagonal architecture: domain, application, and infrastructure clearly separated

## Project Structure

```
src/main/java/com/secureuser/secureuserapi/
├── domain/
│   ├── model/              # JPA entities: User, Role, RoleName (enum)
│   └── repository/         # Port interfaces: UserRepository, RoleRepository
├── application/
│   ├── dto/                # Java records: RegisterRequest, LoginRequest,
│   │                       #   UserResponse, AuthResponse, ApiResponse<T>, ApiError
│   ├── exception/          # Custom exceptions: DuplicateResourceException
│   ├── mapper/             # UserMapper (entity <-> DTO)
│   └── service/            # Use case services: UserRegistrationService, UserLoginService
└── infrastructure/
    ├── config/             # ApplicationConfig (PasswordEncoder bean), DataInitializer
    ├── persistence/        # JpaUserRepository, JpaRoleRepository (Spring Data JPA)
    ├── security/           # JwtTokenProvider, JwtAuthenticationFilter,
    │                       #   UserDetailsServiceImpl, SecurityConfig
    └── web/                # AuthController + GlobalExceptionHandler
```

## Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/sazakku/SecureUserAPI.git
cd SecureUserAPI
```

### 2. Create the PostgreSQL database

```sql
CREATE DATABASE secureuser;
```

### 3. Configure credentials

In `src/main/resources/application.properties`, set your values:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/secureuser
spring.datasource.username=your_user
spring.datasource.password=your_password
jwt.secret=your_secret_key
```

> **Never commit real credentials.** Prefer environment variables or a local override file (`application-local.properties`) excluded from version control.

Alternatively, override with environment variables at runtime:

```bash
SPRING_DATASOURCE_USERNAME=your_user \
SPRING_DATASOURCE_PASSWORD=your_password \
JWT_SECRET=your_secret_key \
./mvnw spring-boot:run
```

### 4. Run the app

```bash
./mvnw spring-boot:run
```

Roles (`ROLE_USER`, `ROLE_ADMIN`) are seeded automatically on first startup.

### 5. Run tests

```bash
./mvnw test
```

> Tests require no database. The controller slice tests use `@WebMvcTest` with a mocked service layer.

## API Reference

Base URL: `http://localhost:8080/api/v1`

### Authentication Endpoints

| Method | Path              | Auth Required | Description                              |
|--------|-------------------|---------------|------------------------------------------|
| POST   | `/auth/register`  | No            | Register a new user account              |
| POST   | `/auth/login`     | No            | Authenticate and receive a JWT token     |

#### POST `/auth/register`

Registers a new user. Assigns `ROLE_USER` automatically.

**Request body:**
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "secret123"
}
```

**Validation rules:**
- `username`: 3–50 characters, alphanumeric + underscore only (`^[a-zA-Z0-9_]+$`)
- `email`: valid email format, max 100 characters
- `password`: 8–128 characters

**Success — 201 Created:**
```json
{
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com",
    "roles": ["ROLE_USER"]
  },
  "message": "User registered successfully",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

#### POST `/auth/login`

Authenticates a user and returns a signed JWT Bearer token.

**Request body:**
```json
{
  "username": "johndoe",
  "password": "secret123"
}
```

**Success — 200 OK:**
```json
{
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "type": "Bearer",
    "username": "johndoe"
  },
  "message": "Login successful",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

### Using the JWT token

Include the token in the `Authorization` header for all protected endpoints:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

## API Conventions

- All endpoints versioned under `/api/v1/`
- Resource nouns only — no verbs in paths
- Success response: `{ "data": ..., "message": "...", "timestamp": "..." }`
- Error response: `{ "error": "...", "code": "...", "timestamp": "..." }`

### Error codes reference

| HTTP Status | Code               | Trigger                                              |
|-------------|--------------------|------------------------------------------------------|
| 400         | `VALIDATION_ERROR` | Bean Validation failure or malformed request body    |
| 401         | `UNAUTHORIZED`     | Missing, expired, or invalid JWT token               |
| 403         | `FORBIDDEN`        | Authenticated but insufficient role                  |
| 404         | `NOT_FOUND`        | Requested resource does not exist                    |
| 409         | `CONFLICT`         | Duplicate resource (username/email already taken)    |
| 500         | `INTERNAL_ERROR`   | Unexpected server error (details never exposed)      |

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

> Built with ❤️ by [@sazakku](https://github.com/sazakku)
