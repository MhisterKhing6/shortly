# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Technology Stack

- **Framework**: Spring Boot 4.0.0
- **Java Version**: 21
- **Build Tool**: Maven
- **Database**: MongoDB (with Spring Data MongoDB)
- **Security**: JWT-based authentication (jjwt 0.11.5)
- **API Documentation**: SpringDoc OpenAPI 2.7.0
- **SMS Notifications**: MNotify API
- **Libraries**: Lombok, Spring WebFlux, AspectJ, BCrypt

## Build and Development Commands

```bash
./mvnw spring-boot:run          # Run the application
./mvnw clean package -DskipTests  # Build (packaging only)
./mvnw test                     # Run all tests
./mvnw test -Dtest=ClassName    # Run a specific test class
```

## Application Configuration

### Required environment variables (see .env)
- `MONGO_URL`: MongoDB connection string
- `MNOTIFY_API`: MNotify API key for SMS notifications
- `JWT_ACCESS_KEY`: Secret key for JWT access tokens
- `JWT_REFRESH_KEY`: Secret key for JWT refresh tokens (note: typo in application.yml as `JWT_REFERESH_KEY`)
- `JWT_EXPIRATIONTIME`: JWT expiration time in milliseconds
- `FRONTEND_HOST`: Frontend server URL for CORS

### Server configuration
- Base context path: `/shortly` — all endpoints are prefixed with it
- Swagger UI: `/shortly/swagger-ui/index.html`
- Health: `/shortly/health`

## Architecture Overview

Parcel and delivery management system for a courier service. Standard Spring Boot layered architecture: Controller → Service (interface + impl) → Repository → MongoDB.

### Business Domain

- **User roles**: ADMIN, RIDER, FRONTDESK, MANAGER
- **Parcel types**: Regular, home delivery, POD (payment on delivery), pickup
- **Flow**: Parcels registered at front desk → assigned to riders → delivered → reconciled
- **DriverReconcilation**: Tracks inbound cost per rider across deliveries. Created/updated when a parcel with `inboundCost > 0` is saved. `ParcelInfo` (embedded snapshot) is built **after** `parcelRepository.save()` so the parcel already has its generated ID.
- **Call center**: Follows up on delivered parcels — updates `callOutCome` (REACHED/UNREACHABLE) and `hasCallCenterSpokenToClient`
- **UserAction**: AOP-based audit log. Every controller method annotated with `@TrackUserAction` auto-saves a `UserAction` document (userId, userName, userEmail, officeId, action, description) via `UserActionAspect`.

### Controllers and their routes

| Controller | Base path | Roles |
|---|---|---|
| `UserController` | `/api-user` | Public (login/register) + authenticated |
| `AdminController` | `/api-admin` | ADMIN, MANAGER |
| `FrontDeskController` | `/api-frontdesk` | FRONTDESK, MANAGER, ADMIN |
| `RiderController` | `/api-rider` | RIDER |
| `OfficeController` | `/api/offices` | Authenticated |
| `ReceiverController` | `/api-receiver` | Authenticated |
| `CallCenterController` | `/api-call-center` | ADMIN, MANAGER |

### Key cross-cutting patterns

**Getting the logged-in user's officeId** (used widely across services):
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
User user = (User) auth.getPrincipal();
String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
    ? user.getOfficeIds().get(0) : null;
```

**Paginated queries with optional filters** use `MongoTemplate` + `Criteria` list + manual `skip/limit` (not Spring Data's `Pageable` on the repository), then wrap in `PageImpl`. See `ParcelServiceImplementation` for the canonical example.

**`@PreAuthorize`** is used on service methods (not controllers) to enforce role access.

**`UserService`** has a manual constructor (not `@AllArgsConstructor`) — add new dependencies to both the field list and the constructor.

### Authentication & Security

- Phone number is the username for authentication
- JWT required for all endpoints except `/api-user/**`, `/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- Stateless sessions (no server-side session storage)

## Database

### MongoDB collections
- `users`, `parcelss` (note double-s), `delivery_assignmentsss` (note triple-s)
- `offices`, `locations`, `shelves`
- `reconcilations`, `driver_reconcilations`
- `rider_status`, `verification_tokens`, `contacts`, `user-actions`

### Indexes
- **Parcel**: `office_delivered_idx`, `office_homedelivery_idx`, `office_called_idx`, `search_parcels_idx`
- **DeliveryAssignments**: `rider_status_idx`, `office_status_idx`, `office_payed_idx`, `rider_phone_status_idx`, `office_assigned_idx`

## Known Issues / Gotchas

- **Collection name typos**: `parcelss`, `delivery_assignmentsss` — do not rename, data is already stored there
- **`JWT_REFERESH_KEY`** typo in `application.yml` — env var is `JWT_REFRESH_KEY` but the yml key is misspelled
- **`UnAthorizeException`** typo — do not rename, it's referenced in multiple places
- **`UserAction.userEmai`** field — missing trailing 'l', already in production data
- **`RateLimitFilter`** — entire class body is commented out; the bucket4j dependency was removed. Do not re-add `bucket4j_jdk17-core` — it uses `java.lang.foreign.Linker` which is a preview API in Java 21 and breaks Lombok annotation processing
- **IDE auto-import risk**: IDEs may auto-import `java.lang.foreign.Linker` — if the build fails with `java.lang.foreign.Linker is a preview API`, check `ParcelServiceImplementation.java` line 3 for a stray import
- **Lombok `annotationProcessorPaths`** in `pom.xml` requires `<version>${lombok.version}</version>` explicitly, otherwise `maven-compiler-plugin 3.14.x` cannot resolve Lombok and all `@Data`/`@Builder` methods are missing at compile time

## Lombok Usage

- `@Data` for getters/setters/equals/hashCode/toString
- `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` together on DTOs
- `@Builder.Default` on fields with default values in `@Builder` classes
- Classes with manual constructors (like `UserService`) cannot use `@AllArgsConstructor` — add fields to both the field declaration and the constructor body
