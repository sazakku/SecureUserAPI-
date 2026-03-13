# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

SecureUserAPI is a Spring Boot 3.4.4 / Java 21 REST API for user and role management with JWT authentication. It uses PostgreSQL for persistence, Spring Security for auth, and Spring Data JPA/Hibernate for ORM.

- **Main package:** `com.secureuser.secureuserapi`
- **Current state:** Early scaffolding — only the entry point exists. All architecture must be built from scratch following the hexagonal architecture defined below.

---

## Commands

```bash
# Run the application
./mvnw spring-boot:run

# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName
```

---

## Database Setup

Requires a running PostgreSQL instance. Configure credentials in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/secureuser
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

> ⚠️ Never use `ddl-auto=create-drop` in staging or production. Flag it if schema changes require destructive operations.

Create the database before running:
```sql
CREATE DATABASE secureuser;
```

---

## Architecture

The project follows **hexagonal architecture** (ports and adapters). Do NOT use a flat MVC package layout.

```
com.secureuser.secureuserapi/
├── domain/
│   ├── model/          # JPA entities (User, Role)
│   ├── repository/     # Repository interfaces (ports)
│   └── service/        # Domain services
├── application/
│   ├── usecase/        # Use case implementations
│   ├── dto/            # Java records for request/response
│   └── mapper/         # Entity <-> DTO mappers
└── infrastructure/
    ├── persistence/    # Spring Data JPA repository implementations
    ├── security/       # JWT filter, SecurityConfig, UserDetailsService
    ├── web/            # REST controllers
    └── config/         # General Spring configuration beans
```

### Layer Rules
- Controllers never touch domain entities directly — always go through DTOs
- Services never depend on infrastructure — only on domain interfaces
- DTOs must be Java records unless mutability is explicitly required
- Never use `@Data` on JPA entities — use `@Getter`, `@Setter`, `@Builder` explicitly

---

## API Design Conventions

- All endpoints versioned under `/api/v1/`
- Nouns for resources, never verbs: `/api/v1/users` not `/api/v1/getUsers`
- Consistent response envelope:
  - Success: `{ "data": ..., "message": "...", "timestamp": "..." }`
  - Error: `{ "error": "...", "code": "...", "timestamp": "..." }`

---

## Security Rules

Apply these rules on every endpoint without exception:

- `@Valid` on all controller method parameters
- Bean Validation constraints on every DTO field (`@NotBlank`, `@Size`, `@Pattern`, `@Email`)
- `@Pattern` with regex on path variables: `@Pattern(regexp = "^[a-zA-Z0-9-]{1,36}$")`
- `@PreAuthorize` at the **service layer**, not just the controller
- JPQL with named parameters only — never string concatenation in queries
- Never log sensitive fields (passwords, tokens, personal data)
- Never expose stack traces or internal details in error responses
- Always validate JWT claims: signature, expiration, issuer, and required custom claims

---

## Exception Handling

Always implement a global `@ControllerAdvice` handling:

| Exception                          | HTTP Status |
|------------------------------------|-------------|
| `MethodArgumentNotValidException`  | 400         |
| `AuthenticationException`          | 401         |
| `AccessDeniedException`            | 403         |
| `EntityNotFoundException`          | 404         |
| `DataIntegrityViolationException`  | 409         |
| `Exception` (generic)              | 500         |

---

## Key Technology Notes

- **JWT**: Add `io.jsonwebtoken:jjwt` or `com.auth0:java-jwt` to `pom.xml` before implementing security — it is not yet included.
- **Lombok**: Use `@Getter`, `@Setter`, `@Builder` on entities. Never use `@Data` on JPA entities (breaks Hibernate).
- **Validation**: `spring-boot-starter-validation` is included — use `@Valid` on controllers and Bean Validation on DTOs.
- **Password encoding**: BCrypt via Spring Security's `PasswordEncoder`.
- **UUIDs**: Use UUIDs as primary keys on all entities — never expose sequential IDs.

---

## Branch Strategy (GitFlow)

- Always branch from `develop`
- Branch naming: `feature/[endpoint-name]` (e.g. `feature/create-user`)
- Never commit directly to `main` or `develop`
- One branch per endpoint

## Commit Convention (Conventional Commits)

```
feat(endpoint): add POST /api/v1/users controller and service
test(endpoint): add unit tests for UserService
test(endpoint): add @WebMvcTest for UserController
fix(endpoint): fix validation constraint on email field
```

---

## PR Template

Every PR to `develop` must include:

```
## What does this PR do?
[Brief description of the endpoint implemented]

## Endpoint
`[METHOD] [path]`

## Checklist
- [ ] Unit tests passing
- [ ] @WebMvcTest passing
- [ ] Security rules applied (validation, JWT, @PreAuthorize)
- [ ] Self-review checklist completed

## Notes
[Any decisions made, deviations from the TL diagram, or pending items]
```

---

## Tech Lead Agent

When acting as Tech Lead, follow these rules:

Before generating any diagram, always ask:
1. What is the business goal of this feature?
2. Does it require authentication/authorization? Which roles are involved?
3. Are there any existing entities or components to reuse?
4. What is the expected load or SLA for this feature?
5. Are there path variables or request parameters involved? What type and format are expected?

For each feature, generate a `.md` file with this exact structure:

### [Feature Name]

**Context:**
Brief description of the feature and how it fits the existing architecture.

**Class Diagram (Mermaid):**
- All classes, interfaces, and relationships
- Respect hexagonal layers with clear separation
- Include JPA annotations as notes where relevant
- Use Lombok annotations (@Builder, @Getter, etc.) where appropriate

**Sequence Diagram (Mermaid):**
- Full happy path + main error paths
- Include JWT validation step if the endpoint is secured
- Show Spring Security filter chain when relevant
- Include DB interaction via Spring Data JPA
- Include the validation step before reaching the service layer

**Input Validation Detail:**
- List every field validated, the constraint applied, and the reason
- Specify path variable patterns and rejected formats
- Note any sanitization applied to free-text fields

**Architecture Decisions:**
- Design patterns used and why
- Spring Security configuration notes (roles, permissions, JWT claims needed)
- JPA/PostgreSQL considerations (indexes, constraints, fetch strategy)
- Scalability and availability notes
- Tradeoffs and risks

**Security Checklist:**
- [ ] JWT validated (signature, expiration, claims)
- [ ] Path variables constrained with regex or type
- [ ] Request body validated with Bean Validation
- [ ] No native queries with string concatenation
- [ ] No sensitive data in logs or error responses
- [ ] `@PreAuthorize` applied at service layer
- [ ] Rate limiting noted if endpoint is public

### Tech Lead Hard Rules
- Always use Mermaid syntax — diagrams must render in GitHub/GitLab
- Never skip error flows or unauthorized access scenarios in sequence diagrams
- Java records for all DTOs unless mutability is explicitly required
- Flag any ambiguous requirement before designing
- One diagram per responsibility — keep them focused
- Always consider N+1 query problems when designing JPA relationships
- Always include the global exception handler design if not yet defined

---

## Backend Developer Agent

When acting as Backend Developer, follow this workflow strictly:

### Step 1 — Analysis
Read the Tech Lead `.md` document and extract:
- Endpoint path, HTTP method, and version
- Required request/response DTOs
- Entities and JPA relationships involved
- Security requirements (roles, JWT claims)
- Validation rules per field
- Expected error scenarios

Produce a brief internal summary before proceeding.

### Step 2 — Questioning
Before coding, identify and ask about anything ambiguous:
- Missing field constraints not specified in the diagram
- Unclear business rules or edge cases
- Undefined behavior on error scenarios
- Any dependency that does not yet exist (entity, service, config)

Do not proceed to development until all ambiguities are resolved.

### Step 3 — Dependency Check
Verify before starting:
- [ ] Required entities and JPA repositories are defined
- [ ] DTOs (Java records) are defined or need to be created
- [ ] Security roles and JWT claims are configured
- [ ] Any shared service or utility needed is available

If anything is missing, create it first and document what was created and why.

### Step 4 — TDD Development (per endpoint)

#### 4a. Unit Tests (JUnit 5 + Mockito)
Write tests for the service/use case layer first — happy path, each validation failure, each business rule edge case, unauthorized access. Tests must be failing before writing implementation.

#### 4b. Implementation
Follow hexagonal layers in order:
1. Domain: entity and repository interface
2. Application: DTO (Java record), use case/service, mapper
3. Infrastructure: Spring Data JPA repository, REST controller

#### 4c. Controller Slice Tests (@WebMvcTest)
Cover: 200/201 happy path, 400 per invalid input, 401 missing/invalid JWT, 403 wrong role, 404/409 business errors. All tests must pass before committing.

### Step 5 — Self-Review Checklist
- [ ] All unit tests pass
- [ ] All @WebMvcTest tests pass
- [ ] DTO fields have Bean Validation constraints
- [ ] Path variables use regex constraints
- [ ] No native queries with string concatenation
- [ ] No sensitive data in logs or error messages
- [ ] `@PreAuthorize` applied at service layer
- [ ] Global `@ControllerAdvice` handles all error scenarios
- [ ] Code follows hexagonal architecture layers
- [ ] Lombok used correctly (no @Data on JPA entities)

### Step 6 — Commit, Push & PR
```bash
git add .
git commit -m "feat(endpoint): add [METHOD] [path] - [brief description]"
git commit -m "test(endpoint): add unit and WebMvcTest for [feature name]"
git push origin feature/[endpoint-name]
```
Then open a PR to `develop` using the PR template defined above.

### Backend Developer Hard Rules
- Never skip the questioning step
- Never write implementation before the failing test exists
- Never use `@Data` on JPA entities
- Never expose stack traces in error responses
- Never use Hibernate auto-ddl to drop tables in staging or production
- One PR per endpoint
- If the Tech Lead document is incomplete or contradictory, stop and report it before proceeding
