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
- **File Storage**: Cloudflare R2 (S3-compatible, accessed via the AWS SDK v2 `software.amazon.awssdk:s3` client with an endpoint override — not AWS S3) for parcel images, uploaded as base64 at parcel creation
- **GPS Tracking**: Jimi IoT / TrackSolid Pro API (`JimiApiService`) for rider device location/track lookups, called via `WebClient`
- **Email**: Mailtrap Sending API (REST, via `WebClient` — not SMTP) for company-verification and new-user-credentials emails, sent from `EmailServiceImplementation`
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
- `R2_ACCOUNT_ID`: Cloudflare account ID (used to build the R2 S3-compatible endpoint)
- `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`: R2 API token credentials (not AWS IAM keys)
- `R2_BUCKET`: R2 bucket for parcel images (default `mnm-parcel-images`)
- `R2_PUBLIC_BASE_URL`: Public base URL for serving uploaded images — an `r2.dev` subdomain or a custom domain bound to the bucket (R2 buckets aren't publicly readable by default)
- `JIMI_APP_KEY`, `JIMI_APP_SECRET`, `JIMI_USER_ID`, `JIMI_USER_PASSWORD_MD5`: Jimi/TrackSolid GPS API credentials (`JIMI_BASE_URL` has a default)
- `MAILTRAP_API_TOKEN`: Mailtrap Sending API token (`MAILTRAP_API_URL`, `MAILTRAP_FROM_NAME` have defaults)
- `MAIL_FROM`: sender email address for outbound mail (default `no-reply@shortly.com`)

### Server configuration
- Base context path: `/shortly` — all endpoints are prefixed with it
- Swagger UI: `/shortly/swagger-ui/index.html`
- Health: `/shortly/health`

## Architecture Overview

Parcel and delivery management system for a courier service. Standard Spring Boot layered architecture: Controller → Service (interface + impl) → Repository → MongoDB.

### Business Domain

- **User roles**: ADMIN, RIDER, FRONTDESK, MANAGER, CALLCENTER, VENDOR, CUSTOMER (`CUSTOMER` is declared but not currently referenced anywhere — unused scaffolding)
- **Parcel types** (`ParcelTypes` enum): `PARCEL`, `ONLINE`, `PICKUP`, `TRANSFER` (office-to-office transfer, set on vendor parcel creation). Independent boolean flags on `Parcel` further classify a parcel: `isPOD` (payment on delivery), `homeDelivery`
- **Flow**: Parcels registered at front desk (or by a vendor) → assigned to riders → delivered → reconciled
- **DriverReconcilation**: Tracks inbound cost per rider across deliveries. Created/updated when a parcel with `inboundCost > 0` is saved. `ParcelInfo` (embedded snapshot) is built **after** `parcelRepository.save()` so the parcel already has its generated ID.
- **Call center**: Follows up on delivered parcels — updates `callOutCome` (REACHED/UNREACHABLE) and `hasCallCenterSpokenToClient`. Role `CALLCENTER` shares read/update access to these endpoints alongside `ADMIN`/`MANAGER` (see `ParcelServiceImplementation`'s call-center methods)
- **UserAction**: AOP-based audit log. Every controller method annotated with `@TrackUserAction` auto-saves a `UserAction` document (userId, userName, userEmail, officeId, action, description) via `UserActionAspect`.
- **Embedded snapshot pattern**: lightweight value objects (`OfficeInfo`, `ParcelInfo`, `RiderInfo`) are copied onto parent documents at the moment of the relevant transition (assignment, reconciliation) rather than referenced live — avoids joins but means the snapshot can drift from the source document if the source changes later.
- **Parcel images**: `ParcelRequest.images`/`VendorParcelRequest.images` (base64 strings) are uploaded to Cloudflare R2 via `R2Service.uploadImages(...)` during parcel creation in `ParcelServiceImplementation`, and the resulting URLs (built from `r2.public-base-url` + object key) are stored on `Parcel.imageUrls`, then copied into `ParcelInfo.imageUrls` at rider-assignment time.
- **Barcode**: `Parcel.barCode` is unique+sparse; `BarcodeGenerator` auto-generates `PARCEL-<year>-<seq>` codes from an atomic per-year counter (`model/Counter.java`) unless the caller supplies their own unique value. No barcode/QR scanning endpoint exists yet — despite the "barcode and qr code" commit title, no QR functionality was actually added.
- **Vendor ("partner") flow**: `VENDOR` users register/track their own parcels without an `officeId` (identified by phone number instead, stored as `Parcel.vendorId`) via `VendorController` (`/api-vendor`), gated with `@PreAuthorize("hasRole('VENDOR')")` on the service methods.
- **Fuel requests**: Riders submit fuel requests (`POST /api-rider/fuel-request`); front desk/manager approve or reject with an amount (`PUT /api-frontdesk/fuel-request/{id}`). Model has a typo'd field `fuleStationPhoneNumber`.

### Controllers and their routes

| Controller | Base path | Roles |
|---|---|---|
| `UserController` | `/api-user` | Public (login/register) + authenticated |
| `AdminController` | `/api-admin` | ADMIN, MANAGER (includes admin/rider-performance dashboards) |
| `FrontDeskController` | `/api-frontdesk` | FRONTDESK, MANAGER, ADMIN (includes fuel-request approval) |
| `RiderController` | `/api-rider` | RIDER (includes fuel-request submission) |
| `OfficeController` | `/api/offices` | Authenticated |
| `ReceiverController` | `/api-receiver` | Authenticated |
| `CallCenterController` | `/api-call-center` | ADMIN, MANAGER, CALLCENTER |
| `VendorController` | `/api-vendor` | VENDOR |
| `TrackingController` | `/api-tracking` | Public tracking endpoint + role-gated status/assignment updates |

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

**Registration branches on role**: `UserService.register()` requires and validates `officeId` for every role except `VENDOR` (which has no office affiliation and is tracked by phone number instead); registering a `MANAGER` also sets them as their office's manager.

**Jimi GPS calls** go through `JimiTokenService` first (caches/refreshes an OAuth token in the `jimi_access_tokens` collection, singleton doc id `"jimi-token"`) before `JimiApiService` signs (MD5) and sends the actual location/track request — never call the Jimi REST API without going through the token service.

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
- `fuel_requests`, `jimi_access_tokens`

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
- **R2 credentials are R2 API tokens, not AWS keys** — `R2Config` uses `StaticCredentialsProvider` with `r2.access-key-id`/`r2.secret-access-key`; don't try to wire AWS IAM/`DefaultCredentialsProvider` here, R2 doesn't support it
- **`r2.public-base-url` must be set** for uploaded image URLs to actually resolve — R2 buckets aren't publicly readable by default the way S3 buckets can be; without a bound `r2.dev` subdomain or custom domain, `Parcel.imageUrls` will contain URLs that 401/404
- **`FuelRequest.fuleStationPhoneNumber`** typo — do not rename, mirrors the pattern of other known field-name typos in this codebase

## Lombok Usage

- `@Data` for getters/setters/equals/hashCode/toString
- `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` together on DTOs
- `@Builder.Default` on fields with default values in `@Builder` classes
- Classes with manual constructors (like `UserService`) cannot use `@AllArgsConstructor` — add fields to both the field declaration and the constructor body
