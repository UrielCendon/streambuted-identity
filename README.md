# Identity Service — StreamButed

Microservicio de autenticación, autorización y gestión de usuarios de la plataforma **StreamButed**.

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| Runtime | Java 21 + Spring Boot 3.3 |
| Base de datos | PostgreSQL 16+ |
| Seguridad | Spring Security + JWT (RS256) + JWKS |
| Protocolo externo | REST (HTTPS) bajo `/api/v1` |
| Protocolo interno | gRPC en puerto 9091 |
| Mensajería | Transactional Outbox + RabbitMQ (AMQP) |
| Tests | JUnit 5 + Mockito |

---

## Requisitos previos

- Opción A: Java 21 + Maven 3.9+ + PostgreSQL 16 + RabbitMQ
- Opción B: Docker Desktop

## Archivos de despliegue local

| Archivo | Propósito |
|---|---|
| [Dockerfile](Dockerfile) | Construye la imagen de la aplicación con multi-stage build y usuario no root |
| [.dockerignore](.dockerignore) | Reduce el contexto de build de Docker |
| [.gitignore](.gitignore) | Evita subir artefactos locales como `target/` y `.env` |

---

## Configuración del entorno

Todas las credenciales se inyectan a través de **variables de entorno**.  
Nunca edites el secreto directamente en `application.yml`.

### Opción recomendada: Docker Desktop

1. Configura el archivo `.env` unificado en la raíz del repositorio (`StreamButed/.env`).
2. Reemplaza en ese `.env` todos los valores `CHANGE_ME_*` por credenciales reales.
3. Ejecuta:

```bash
docker compose up -d --build
```

Ejecuta el comando desde la **raíz del monorepo**. Con eso se levantan PostgreSQL, RabbitMQ,
identity-service y catalog-service con una sola orquestación.

### Variables que usa la aplicación

- `DB_URL`: URL de PostgreSQL usada por Spring Boot.
- `DB_USERNAME`: usuario de la base de datos.
- `DB_PASSWORD`: contraseña de la base de datos.
- `JWT_ISSUER`: valor del claim `iss` emitido en los access tokens.
- `JWT_KEY_ID`: (opcional) `kid` usado en el header del JWT y en el JWKS.
- `JWT_RSA_PRIVATE_KEY_PEM` / `JWT_RSA_PRIVATE_KEY_BASE64`: (opcional) clave privada RSA para firmar (PKCS#8).
- `JWT_RSA_PUBLIC_KEY_PEM` / `JWT_RSA_PUBLIC_KEY_BASE64`: (opcional) clave pública RSA para publicar en JWKS (X.509). Si no se provee, puede derivarse de la privada.
- `RABBITMQ_HOST`: host del broker RabbitMQ.
- `RABBITMQ_PORT`: puerto AMQP del broker.
- `RABBITMQ_USERNAME`: usuario AMQP.
- `RABBITMQ_PASSWORD`: contraseña AMQP.
- `RABBITMQ_VHOST`: virtual host de RabbitMQ.
- `SERVER_PORT`: puerto HTTP del servicio.
- `GRPC_PORT`: puerto gRPC del servicio.

> Nota: si no se configura ninguna clave RSA (`JWT_RSA_*`), el servicio genera un par de claves **efímero** al iniciar.
> Eso es útil para desarrollo, pero invalida access tokens existentes tras reinicios.

### Opción manual sin Docker para la app

En PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/streambuted_identity"
$env:DB_USERNAME="streambuted"
$env:DB_PASSWORD="your_secure_password"

$env:JWT_ISSUER="http://localhost:8081"
$env:JWT_KEY_ID=""
$env:JWT_RSA_PRIVATE_KEY_PEM=""
$env:JWT_RSA_PUBLIC_KEY_PEM=""
$env:JWT_RSA_PRIVATE_KEY_BASE64=""
$env:JWT_RSA_PUBLIC_KEY_BASE64=""

$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_USERNAME="streambuted"
$env:RABBITMQ_PASSWORD="change_me_rabbit_password"

$env:GRPC_PORT="9091"
```


### Crear la base de datos en PostgreSQL

```sql
CREATE DATABASE streambuted_identity;
CREATE USER streambuted WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE streambuted_identity TO streambuted;
```

Las tablas (`user_account`, `user_profile`, `refresh_token`) se crean  
automáticamente con `spring.jpa.hibernate.ddl-auto: update` en modo desarrollo.

---

## Ejecución

### Con Docker Compose

```bash
docker compose up -d --build
```

Si quieres parar todo:

```bash
docker compose down
```

### Manual

```bash
# 1. Clonar y entrar al directorio
cd services/identity-service

# 2. Compilar y empaquetar (saltando tests para inicio rápido)
mvn clean package -DskipTests

# 3. Ejecutar
java -jar target/identity-service-1.0.0.jar
```

El servicio estará disponible en:
- **REST API**: `http://localhost:8081/api/v1`
- **gRPC**: `localhost:9091`
- **Actuator**: `http://localhost:8081/actuator/health`

---

## Endpoints REST

### Autenticación (públicos)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/v1/auth/register` | Registro de nuevo usuario (rol: LISTENER) |
| `POST` | `/api/v1/auth/login` | Login, retorna `accessToken` en JSON y `refresh_token` en cookie HttpOnly |
| `POST` | `/api/v1/auth/refresh` | Lee `refresh_token` desde cookie, rota token y emite nuevo `accessToken` |
| `POST` | `/api/v1/auth/logout` | Limpia cookie `refresh_token` y elimina el token persistido |
| `GET` | `/api/v1/auth/.well-known/jwks.json` | Publica JWKS (claves públicas) para validación local de access tokens |

**Ejemplo — Register:**
```json
POST /api/v1/auth/register
{
  "email": "user@example.com",
  "username": "myusername",
  "password": "SecurePass1!"
}
```

**Respuesta exitosa (201):**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid-opaque-token",
  "role": "listener",
  "expiresIn": 900
}
```

### Guía rápida Frontend (cookies + access token en memoria)

1. Envía `POST /api/v1/auth/login` con `withCredentials: true` para que el navegador almacene la cookie HttpOnly `refresh_token`.
2. Guarda únicamente `accessToken` en memoria (no en LocalStorage).
3. Para renovar sesión, llama `POST /api/v1/auth/refresh` con `withCredentials: true` y sin body de refresh token.
4. Para cerrar sesión, llama `POST /api/v1/auth/logout` con `withCredentials: true`.

Ejemplo con Axios:

```ts
const loginResponse = await axios.post(
  "/api/v1/auth/login",
  { email, password },
  { withCredentials: true }
);

const { accessToken } = loginResponse.data;

const refreshResponse = await axios.post(
  "/api/v1/auth/refresh",
  {},
  { withCredentials: true }
);

await axios.post(
  "/api/v1/auth/logout",
  {},
  { withCredentials: true }
);
```

### Usuarios (requieren Bearer JWT)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET`   | `/api/v1/users/me` | Perfil completo del usuario autenticado |
| `PATCH` | `/api/v1/users/promote` | Promueve LISTENER → ARTIST (irreversible) |

**Header requerido:**
```
Authorization: Bearer <accessToken>
```

### Formato de error (todos los endpoints)

```json
{
  "error": "InvalidCredentialsException",
  "message": "Invalid email or password",
  "statusCode": 401,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## gRPC — TokenValidator

Usado internamente por otros microservicios para validar JWTs sin pasar por el API Gateway.

**Proto:** `src/main/proto/token_validator.proto`

```protobuf
service TokenValidator {
    rpc ValidateToken(TokenRequest) returns (TokenResponse);
}
```

**Respuesta exitosa:**
```json
{
  "user_id": "uuid",
  "role": "artist",
  "email": "user@example.com",
  "is_active": true,
  "is_valid": true,
  "error_message": ""
}
```

---

## Mensajería — RabbitMQ

El servicio usa el patrón **Transactional Outbox** para evitar perder eventos entre la base de datos y RabbitMQ.

Flujo de promoción:
1. `UserServiceImpl` cambia la cuenta de `LISTENER` a `ARTIST`.
2. En la misma transacción, guarda un registro en la tabla `outbox` con `event_type = USER_PROMOTED` y `status = PENDING`.
3. `OutboxProcessor` ejecuta cada 5 segundos y publica los eventos pendientes a RabbitMQ.
4. Si la publicación funciona, marca el registro como `PROCESSED`.
5. Si falla, incrementa `retry_count`; al superar el umbral, lo marca `FAILED`.

Cuando un usuario es promovido a ARTIST, el payload publicado es:

- **Exchange:** `identity.events` (topic, durable)
- **Routing key:** `user.promoted`
- **Queue:** `identity.user.promoted`

**Payload del evento:**
```json
{
  "eventId": "uuid",
  "userId": "uuid",
  "email": "user@example.com",
  "username": "myusername",
  "previousRole": "listener",
  "newRole": "artist",
  "promotedAt": "2024-01-15T10:30:00Z"
}
```

### Tablas relacionadas

- `user_account`: cuenta y rol.
- `user_profile`: perfil visible.
- `refresh_token`: refresh tokens revocados o activos.
- `outbox`: eventos pendientes de publicación.

---

## Suite de pruebas

```bash
# Ejecutar todas las pruebas unitarias
mvn test

# Ejecutar con reporte de cobertura (requiere JaCoCo)
mvn test jacoco:report
# Reporte en: target/site/jacoco/index.html
```

### Clases de prueba

| Clase | Componente cubierto | Escenarios |
|-------|--------------------|-|
| `AuthServiceImplTest` | `AuthService` | Registro exitoso, email duplicado, login OK, contraseña incorrecta, cuenta inactiva, refresh rotation, tokens expirados/revocados |
| `JwtServiceTest` | `JwtService` (TokenProvider) | Generación, validación, claims, firma incorrecta, token expirado, conversión de unidades |
| `UserServiceImplTest` | `UserService` | Perfil OK, usuario no encontrado, promoción exitosa, ya artista, admin, fallo de persistencia |
| `TokenValidatorGrpcServiceTest` | `TokenValidatorGrpcService` | Token válido, inactivo, malformado, expirado, usuario eliminado, siempre onCompleted |

---

## Estructura del proyecto

```
services/identity-service/
├── .dockerignore
├── .gitignore
├── Dockerfile
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/streambuted/identity/
    │   │   ├── IdentityServiceApplication.java
    │   │   ├── controller/
    │   │   │   ├── AuthController.java
    │   │   │   └── UserController.java
    │   │   ├── domain/
    │   │   │   ├── Role.java
    │   │   │   ├── OutboxEntity.java
    │   │   │   ├── OutboxStatus.java
    │   │   │   ├── UserAccountEntity.java
    │   │   │   ├── UserProfileEntity.java
    │   │   │   └── RefreshTokenEntity.java
    │   │   ├── dto/
    │   │   │   ├── RegisterRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── RefreshTokenRequest.java
    │   │   │   ├── LoginResponse.java
    │   │   │   ├── UserProfileResponse.java
    │   │   │   └── ErrorResponse.java
    │   │   ├── exception/
    │   │   │   ├── IdentityException.java
    │   │   │   ├── EmailAlreadyExistsException.java
    │   │   │   ├── InvalidCredentialsException.java
    │   │   │   ├── InvalidRefreshTokenException.java
    │   │   │   ├── RolePromotionException.java
    │   │   │   ├── UserNotFoundException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── grpc/
    │   │   │   └── TokenValidatorGrpcService.java
    │   │   ├── messaging/
    │   │   │   ├── RabbitMqConfig.java
    │   │   │   ├── UserPromotedEvent.java
    │   │   │   └── IdentityEventPublisher.java
    │   │   ├── scheduler/
    │   │   │   └── OutboxProcessor.java
    │   │   ├── repository/
    │   │   │   ├── UserAccountRepository.java
    │   │   │   ├── UserProfileRepository.java
    │   │   │   ├── RefreshTokenRepository.java
    │   │   │   └── OutboxRepository.java
    │   │   ├── security/
    │   │   │   ├── JwtProperties.java
    │   │   │   ├── JwtService.java
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   └── SecurityConfig.java
    │   │   └── service/
    │   │       ├── AuthService.java
    │   │       ├── AuthServiceImpl.java
    │   │       ├── UserService.java
    │   │       └── UserServiceImpl.java
    │   ├── proto/
    │   │   └── token_validator.proto
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/streambuted/identity/service/
        │   ├── AuthServiceImplTest.java
        │   ├── JwtServiceTest.java
        │   ├── UserServiceImplTest.java
        │   └── TokenValidatorGrpcServiceTest.java
        └── resources/
            └── application-test.yml
```
