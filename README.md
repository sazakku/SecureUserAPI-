# SecureUserAPI

A secure REST API for user and role management built with Spring Boot 3, Java 21, and JWT authentication. Designed following **hexagonal architecture** (ports and adapters) with TDD and RBAC.

## Tech Stack

- Java 21
- Spring Boot 3.4.4
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- JJWT 0.12.6 (`io.jsonwebtoken`)
- Lombok
- Maven
- JUnit 5 + Mockito

## Features

- Role-based access control (RBAC) with `ROLE_USER` and `ROLE_ADMIN`
- JWT authentication (signature, expiration, issuer validation)
- BCrypt password encoding
- Java records as DTOs — immutable and validated
- Bean Validation on all inputs (`@Valid`, `@NotBlank`, `@Email`, `@Pattern`, etc.)
- Global exception handler (`@RestControllerAdvice`) covering 400/401/403/404/409/500
- Consistent response envelope for success and error responses
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
│   ├── mapper/             # UserMapper (entity <-> DTO)
│   └── usecase/            # Use case implementations (added per endpoint)
└── infrastructure/
    ├── persistence/        # JpaUserRepository, JpaRoleRepository (Spring Data JPA)
    ├── security/           # JwtTokenProvider, JwtAuthenticationFilter,
    │                       #   UserDetailsServiceImpl, SecurityConfig
    ├── web/                # REST controllers + GlobalExceptionHandler
    └── config/             # ApplicationConfig (PasswordEncoder bean)
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

> Never commit real credentials. Use environment variables or a local override file.

### 4. Run the app

```bash
./mvnw spring-boot:run
```

### 5. Run tests

```bash
./mvnw test
```

## API Conventions

- All endpoints versioned under `/api/v1/`
- Resource nouns only — no verbs in paths
- Success response: `{ "data": ..., "message": "...", "timestamp": "..." }`
- Error response: `{ "error": "...", "code": "...", "timestamp": "..." }`

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

> Built with ❤️ by [@sazakku](https://github.com/sazakku)
