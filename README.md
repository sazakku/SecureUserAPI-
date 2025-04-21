# SecureUserAPI 🔐

A secure REST API for user and role management using Spring Boot 3, Java 21, and JWT authentication.

## 🚀 Features

- User registration and login
- Role-based access control (RBAC)
- JWT token-based authentication
- Secure password handling with BCrypt
- DTOs for request/response separation
- PostgreSQL for persistence
- JPA + Hibernate
- Exception handling
- Input validation

## 🧱 Tech Stack

- Java 21
- Spring Boot 3.4.4
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT
- Lombok
- Maven

## 🛠️ Project Structure

```text
    src/
     └── main/
         └── java/
             └── com/secureuser/api/
                 ├── controller     # REST Controllers
                 ├── dto            # Data Transfer Objects
                 ├── model          # Entities (User, Role)
                 ├── repository     # Data access layer
                 ├── service        # Business logic
                 ├── security       # JWT config, filters, auth
                 └── config         # General config
         └── resources/
             ├── application.yml
             └── schema.sql
```

## 🔧 Running the Project

1. **Clone the repo**
   ```bash
   git clone https://github.com/tuusuario/secureuserapi.git
   cd secureuserapi
2. **Create PostgreSQL DB (example):**

    ```sql
    CREATE DATABASE secureuser;
3. **Set your DB credentials in application.yml:**
   ```yaml
    spring:
      datasource:
        url: jdbc:postgresql://localhost:5432/secureuser
        username: your_user
        password: your_password
4. **Run the app**

    ```bash
    ./mvnw spring-boot:run
   
5. **Access Swagger (optional if added):**

    ```bash
    http://localhost:8080/swagger-ui.html

##✅ **License**
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.



> Built with ❤️ by [@sazakku](https://github.com/sazakku)