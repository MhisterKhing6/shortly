# Shortly: Parcel and Delivery Management API

Shortly is the backend for a multi-company courier service. It covers the whole life of a parcel: front-desk intake (or partner/vendor submission), shelving, rider assignment and delivery, counter pickup, cash reconciliation, driver payments, customer self-service, and GPS checks on riders.

It is a Spring Boot 4 REST API on Java 21, storing data in MongoDB.

---

## Contents

1. [Tech stack](#1-tech-stack)
2. [Quick start](#2-quick-start)
3. [Environment variables](#3-environment-variables)
4. [Project layout](#4-project-layout)
5. [Architecture](#5-architecture)
6. [Security and authentication](#6-security-and-authentication)
7. [Roles and permissions](#7-roles-and-permissions)
8. [Business flows](#8-business-flows)
9. [API reference](#9-api-reference)
10. [Data model and MongoDB collections](#10-data-model-and-mongodb-collections)
11. [Enums](#11-enums)
12. [External integrations](#12-external-integrations)
13. [Error handling](#13-error-handling)
14. [Scheduled jobs, async work and indexes](#14-scheduled-jobs-async-work-and-indexes)
15. [Testing](#15-testing)
16. [Build, Docker and deployment](#16-build-docker-and-deployment)
17. [Root-level files](#17-root-level-files)
18. [Known issues and gotchas](#18-known-issues-and-gotchas)

---

## 1. Tech stack

| Area | Technology |
|---|---|
| Framework | Spring Boot **4.0.0** (`spring-boot-starter-webmvc`, `-security`, `-validation`, `-data-mongodb`, `-webflux`, `-actuator`) |
| Language | Java **21** |
| Build | Maven (wrapper included: `./mvnw`) |
| Database | MongoDB (Spring Data MongoDB + `MongoTemplate`); transactions need a replica set |
| Auth | JWT (`io.jsonwebtoken:jjwt` 0.11.5, HS256) + BCrypt password hashes |
| API docs | SpringDoc OpenAPI 2.7.0 (Swagger UI) |
| HTTP client | Spring `WebClient` (WebFlux) for all outbound calls |
| Audit logging | Spring AOP + AspectJ (`@TrackUserAction`) |
| File storage | Cloudflare R2 through the AWS SDK v2 S3 client (2.26.27) |
| SMS | MNotify |
| Email | Mailtrap Sending API (REST, not SMTP) |
| GPS | Jimi IoT / TrackSolid Pro open API |
| Maps | Google Routes API (optional) |
| Payments | Paystack (transaction verification) |
| Boilerplate | Lombok |

---

## 2. Quick start

### Prerequisites

- JDK 21
- A MongoDB **replica set** (MongoDB Atlas works; a standalone `mongod` will fail on transactional writes)
- Credentials for MNotify, Mailtrap, Cloudflare R2 and Jimi (see [section 3](#3-environment-variables))

### Run locally

```bash
cp .env.example .env        # then fill in real values
set -a; source .env; set +a # export the variables into your shell
./mvnw spring-boot:run
```

Spring Boot does **not** read `.env` by itself. Export the variables into the shell (as above), or set them in your IDE's run configuration.

Once it is running:

| What | URL |
|---|---|
| Base URL | `http://localhost:8080/shortly` |
| Swagger UI | `http://localhost:8080/shortly/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/shortly/v3/api-docs` |
| Public health check | `GET http://localhost:8080/shortly/api-user/health` (returns `OK Ok`) |

### Other Maven commands

```bash
./mvnw clean package -DskipTests   # build target/shortly-0.0.1-SNAPSHOT.jar
./mvnw test                        # run all tests
./mvnw test -Dtest=ClassName       # run one test class
java -jar target/shortly-*.jar     # run the built jar
```

### First-time setup of a company

1. `POST /shortly/api-company/register` creates a disabled company and a disabled ADMIN user, then emails a verification link.
2. The user clicks the link; the frontend calls `POST /shortly/api-company/verify-email` with the token. The company and its admin are enabled.
3. The admin logs in with `POST /shortly/api-user/login` and creates locations, offices (stations) and staff through `/api-admin`.
4. After deploying the driver-payments feature on an existing database, an admin runs `POST /shortly/api-admin/driver-payments/migrate` once.

---

## 3. Environment variables

A full template is in [.env.example](.env.example). Configuration lives in [application.yml](src/main/resources/application.yml); most values come from environment variables.

### 3.1 Required

| Variable | Read by | Purpose | If missing |
|---|---|---|---|
| `MONGO_URL` | `spring.mongodb.uri` | MongoDB connection string. Must be a replica set. | App cannot connect |
| `JWT_ACCESS_KEY` | `JWTConfig` (directly) | HMAC key that signs access tokens. Use 32+ random bytes. | App will not start |
| `JWT_REFRESH_KEY` | `JWTConfig` (directly) | HMAC key that signs refresh tokens. | App will not start |
| `JWT_EXPIRATION_TIME` | `JWTConfig` (directly) | Access-token lifetime in ms. Refresh tokens last 7 times this. | App will not start |
| `MAILTRAP_API_TOKEN` | `mailtrap.token` | Mailtrap Sending API token. | App will not start |
| `R2_ACCOUNT_ID` | `r2.account-id` | Cloudflare account id; builds `https://<id>.r2.cloudflarestorage.com`. | App will not start |
| `R2_ACCESS_KEY_ID` | `r2.access-key-id` | R2 API token key id. | App will not start |
| `R2_SECRET_ACCESS_KEY` | `r2.secret-access-key` | R2 API token secret. | App will not start |
| `R2_PUBLIC_BASE_URL` | `r2.public-base-url` | Public base URL for images (an `r2.dev` subdomain or a custom domain bound to the bucket). | App will not start; a wrong value gives image URLs that 401/404 |
| `MNOTIFY_API` | `mnotify.accessKey` | MNotify API key. | App starts; every SMS fails (OTP, credentials, notifications) |
| `JIMI_APP_KEY`, `JIMI_APP_SECRET`, `JIMI_USER_ID`, `JIMI_USER_PASSWORD_MD5` | `jimi.*` | TrackSolid Pro API credentials. The password is sent as its MD5 hash. | App starts; GPS endpoints return 502 |

> The JWT variables are read by name in `JWTConfig` (`@Value("${JWT_ACCESS_KEY}")` etc.). The `jwt:` block in `application.yml` (`JWT_REFERESH_KEY`, `JWT_EXPIRATIONTIME`) is **not used** by any code. Set `JWT_REFRESH_KEY` and `JWT_EXPIRATION_TIME`.

### 3.2 Optional (with defaults)

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port. |
| `FRONTEND_HOST` | none | Frontend origin. Fallback for `APP_FRONTEND_URL`. |
| `APP_FRONTEND_URL` | `FRONTEND_HOST`, else `http://localhost:3000` | Base of links sent in email and SMS (`/verify-email`, `/receive?t=…`). |
| `EMAIL_VERIFICATION_PATH` | `/verify-email` | Frontend path of the verification page. Link = `APP_FRONTEND_URL + path + ?token=…`. |
| `EMAIL_VERIFICATION_EXPIRY_MS` | `86400000` (24 h) | Lifetime of a company verification link. |
| `MAIL_FROM` | `no-reply@shortly.com` | Sender address. Must be on a domain verified in Mailtrap. |
| `MAILTRAP_API_URL` | `https://send.api.mailtrap.io/api/send` | Mailtrap endpoint. |
| `MAILTRAP_FROM_NAME` | `Shortly` | Sender display name. |
| `R2_BUCKET` | `mnm-parcel-images` | R2 bucket for parcel images. |
| `JIMI_BASE_URL` | `https://open.tracksolidpro.com/route/rest` | Jimi API endpoint. |
| `GOOGLE_MAPS_API_KEY` | empty | Google Routes API key. Empty means distance = straight line × 1.35, labelled `ESTIMATE`. |
| `PAYSTACK_SECRET_KEY` | empty | Paystack secret key for verifying customer delivery payments. Empty means bookings cannot be verified. |
| `RECEIVER_ARRIVAL_SMS` | `false` | When `true`, texts receivers a "parcel arrived" SMS with their `/receive` link (one SMS per parcel). |

### 3.3 Tuning properties (no env var in `application.yml`)

These are read with `@Value` and have defaults in code. Spring maps environment variables to them (dots and dashes become underscores, uppercase), so `shortly.tracking.visit-radius-m` can be set as `SHORTLY_TRACKING_VISIT_RADIUS_M`.

| Property | Default | Meaning |
|---|---|---|
| `shortly.delivery-location.restrict-to-ghana` | `true` | Reject pins outside lat 4.5–11.3, lng −3.4–1.3. |
| `shortly.integrity.cron` | `0 30 2 * * *` | When the nightly driver-money check runs (Africa/Accra). |
| `shortly.tracking.visit-radius-m` | `150` | Rider counts as having visited a drop-off within this radius. |
| `shortly.tracking.visit-grace-min` | `30` | Grace period around the marked time when looking for a visit. |
| `shortly.tracking.marked-away-m` | `300` | Flag a parcel marked done this far from its drop-off pin. |
| `shortly.tracking.stop-speed-kmh` | `3` | Below this speed the rider is "stopped". |
| `shortly.tracking.stop-min-minutes` | `5` | Minimum length of a stop worth reporting. |
| `shortly.tracking.max-gap-min` | `15` | GPS gap longer than this is reported as a gap. |
| `shortly.tracking.detour-corridor-m` | `1500` | Distance off the expected path that counts as a detour. |
| `shortly.tracking.detour-min-minutes` | `3` | Minimum detour duration. |
| `shortly.tracking.off-task-stop-radius-m` | `200` | A stop farther than this from any drop-off/station is off-task. |
| `shortly.tracking.plan-corridor-google-m` | `500` | Corridor around a Google-planned route. |
| `shortly.tracking.plan-corridor-estimate-m` | `1500` | Corridor around an estimated (straight-line) route. |

The tracking defaults mirror the frontend's `trackUtils.ts`. Change both together.

### 3.4 Hard-coded values worth knowing

| Where | Value |
|---|---|
| `mnotify.sender` | `Synergetics` (SMS sender ID) |
| `mnotify.baseUrl` | `https://api.mnotify.com/api/sms` |
| `server.servlet.context-path` | `/shortly` |
| `springdoc.dev-url` | `https://backend.mandmservicescorp.org/shortly` |
| `NotificationUtil.loginCredentials` | Login link to the hosted frontend is written into the SMS text |
| Jackson | `accept-case-insensitive-enums: true`, `fail-on-null-for-primitives: false` |

### 3.5 CI secrets (GitHub Actions)

`AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, used by both workflows in `.github/workflows/`.

---

## 4. Project layout

All Java code is under `src/main/java/shortly/mandmcorp/dev/shortly/`.

```
ShortlyApplication.java        Entry point; @EnableMongoAuditing
annotation/                    @TrackUserAction (audit-log marker)
aspect/                        UserActionAspect (writes UserAction documents)
config/                        Spring configuration (see below)
  security/                    SecurityConfig, JWTConfig, JWTAuthenticationFilter
controller/                    REST controllers (one per area)
dto/request/                   Request bodies (validated with jakarta.validation)
dto/response/                  Response bodies and views (CustomerViews, DriverPaymentViews, ...)
enums/                         Domain enums
exceptions/                    Custom exceptions + GlobalExceptionHandler
model/                         MongoDB documents and embedded value objects
repository/                    Spring Data MongoDB repositories
security/                      UserDetailService (loads User by phone number)
service/
  common/                      IdempotencyService
  company/                     Company registration and email verification
  contact/                     Contact book search
  counter/                     Counter pickups, cash movements, daily cash-up, POD payouts
  customer/                    Public customer portal: links, OTP, quotes, Paystack bookings
  dashboard/                   Admin, revenue and rider-performance dashboards
  driverpay/                   Driver records, fee lines, payouts, nightly integrity check
  email/                       Mailtrap email sender
  notification/                SMS (MNotify) and email notification senders
  office/                      Locations, offices (stations), shelves, pickup charge
  parcel/                      Parcel intake, search, updates, vendor parcels, call centre
  pricing/                     Delivery price tables, rules, Google road distance
  rider/                       Assignments, delivery status, reconciliation, fuel requests
  storage/                     R2Service (image upload)
  tracking/                    Jimi GPS, rider tracking, delivery checks, public tracking
  user/                        Users, login, password reset, profiles
  vendor/                      "Become a partner" applications
utils/                         Pure helpers and rules (see below)
```

### Configuration classes (`config/`)

| Class | What it does |
|---|---|
| `SecurityConfig` | Stateless security chain, public routes, CORS, BCrypt encoder |
| `JWTConfig` | Creates and validates access and refresh tokens |
| `JWTAuthenticationFilter` | Reads `Authorization: Bearer …`, loads the user, sets the security context; enables `@PreAuthorize` |
| `MongoTransactionConfig` | `MongoTransactionManager` and `TransactionTemplate` |
| `IndexInitializer` | Creates indexes at startup (auto index creation is off) |
| `R2Config` | `S3Client` pointed at R2 (region `auto`, path-style) |
| `JimiConfig`, `MNotifyConfig` | `@ConfigurationProperties` for `jimi.*` and `mnotify.*` |
| `WebClientConfig` | Shared `WebClient` and `WebClient.Builder` beans |
| `AsyncConfig` | `taskExecutor` thread pool (core 5, max 10, queue 100) for `@Async` SMS/email |
| `SchedulingConfig` | Enables `@Scheduled` |
| `OpenApiConfig` | Swagger metadata and the "Bearer Authentication" scheme |
| `FrontEndServerConfig` | Binds `backend-server.baseUrl` (not set in yml, so it is null) |
| `MongoConfig` | Empty file |
| `RateLimitFilter` | Entire body commented out |

### Utilities (`utils/`)

| Class | Purpose |
|---|---|
| `CurrentUser` | Logged-in user, company id, manager office scoping (`scopedOfficeId`, `requireRiderInScope`) |
| `BarcodeGenerator` | `PARCEL-<year>-<6-digit seq>` from an atomic per-year counter |
| `BusinessDay` | Station-local days in `Africa/Accra`; parses `yyyy-MM-dd` |
| `CounterPickupRules` | What a customer owes at the counter and payment validation |
| `DeliveryLocationValidator` | Lat/lng validation and the Ghana bounding box |
| `PhoneNumberUtils` | `hasCountryCode`, `normalizeGhana` (to `+233XXXXXXXXX`) |
| `GeoUtils` | Haversine distance |
| `Money` | Money as integer pesewas (`p`) and back to cedis (`c`) to avoid float drift |
| `OtpUtil` | 6-digit OTPs and random user passwords |
| `NotificationUtil` | SMS message templates |
| `ReconciliationValidator` | Validates rider reconciliation lines |
| `DriverIDFormatter`, `ParcelMapper`, `OfficeMapper`, `UserMapper` | Mapping and formatting |
| `WebRequestUtil` | Small `WebClient` POST helper used by SMS |

---

## 5. Architecture

Standard layered Spring Boot:

```
HTTP → JWTAuthenticationFilter → Controller → Service (@PreAuthorize) → Repository / MongoTemplate → MongoDB
                                     │
                                     └─ @TrackUserAction → UserActionAspect → user-actions collection
```

Conventions used throughout:

- **Access rules live on service methods** (`@PreAuthorize`), not on controllers.
- **Company isolation.** Every user (except legacy users) has a `companyId`. Parcel, user, office and money queries add a `companyId` criterion so companies never see each other's data.
- **Office scoping.** `CurrentUser.scopedOfficeId(requested)` forces a MANAGER to their own office; an ADMIN gets the office they ask for (`null` = all). FRONTDESK users work on their first office (`officeIds[0]`).
- **Paginated search** uses `MongoTemplate` with a list of `Criteria`, manual `skip`/`limit`, wrapped in `PageImpl`.
- **Embedded snapshots.** `OfficeInfo`, `ParcelInfo`, `RiderInfo` are copied onto documents at the moment of a transition (assignment, reconciliation) instead of being joined. They can drift from the source later.
- **Transactions + conditional updates.** Assignment, payouts and reversals, vendor batches, POD payouts and customer bookings run in a `TransactionTemplate` and also claim records with conditional updates (`Parcel.activeAssignmentId`, fee line `state`/`payoutId`), so a concurrent duplicate gets HTTP 409.
- **Idempotency.** Endpoints that accept an `Idempotency-Key` header store the result ids for 24 hours; a repeat returns the same result, a repeat while the first is still running gets 409.
- **Money** is computed in integer pesewas (`Money`) and rounded to 2 dp.
- **Public endpoints never return entities.** They return `CustomerViews.*` objects only.

---

## 6. Security and authentication

### Login

`POST /shortly/api-user/login`

```json
{ "phoneNumber": "+233241234567", "password": "secret123" }
```

The phone number is the username. The response contains `userId`, `token`, `phoneNumber`, `name`, `role`, `departmentRole`, `companyId`, `companyName` and the user's first `office`. Login is refused with 403 if the user's company has not verified its email.

Send the token on every protected request:

```
Authorization: Bearer <token>
```

### Tokens

- HS256, subject = phone number, claims `role`, `name`, `email`.
- Access token lifetime = `JWT_EXPIRATION_TIME` ms. A refresh token (7 times longer, signed with `JWT_REFRESH_KEY`) can be generated, but no refresh endpoint is exposed.
- Expired token → `401 {"message":"JWT token has expired","data":null}`. Bad token → `401 {"message":"Invalid JWT token","data":null}`.
- Sessions are stateless; CSRF is disabled.

### Public routes (no token needed)

| Pattern | Notes |
|---|---|
| `/api-user/**` | Login, password reset, customer portal, health. **Every** route under `/api-user` is open at the filter level; see [section 18](#18-known-issues-and-gotchas). |
| `/api-company/register`, `/api-company/verify-email`, `/api-company/resend-verification` | Company signup |
| `POST /api-vendor/applications` | "Become a partner" form |
| `/api-tracking/*/track` | Public parcel tracking |
| `/health/**`, `/error` | `/error` must be open, or errors surface as an empty 403 |
| `/swagger-ui/**`, `/v3/api-docs/**` | API docs |

Everything else requires a valid token, then role checks on the service method.

### CORS

All origins (`*` pattern), methods `GET POST PUT DELETE OPTIONS`, all headers, credentials allowed.

### Passwords

- Stored as BCrypt hashes.
- Staff created by an admin get a random password, sent to them by SMS and (if they have an email) by email.
- Company signup passwords must be 7+ characters with at least one letter and one digit.
- Password reset: `POST /api-user/request-password-reset` texts a 6-digit OTP and returns a verification id; `POST /api-user/reset-password` with that id, the code and the new password. Codes expire after 5 minutes.

---

## 7. Roles and permissions

`UserRole`: `SUPERADMIN`, `ADMIN`, `MANAGER`, `FRONTDESK`, `RIDER`, `CALLCENTER`, `VENDOR`, `CUSTOMER`.

| Role | Who | Main abilities |
|---|---|---|
| `SUPERADMIN` | Platform level | Appears only in some `@PreAuthorize` rules (pricing review, partner applications, integrity run, migration). No code creates one. |
| `ADMIN` | Company admin | Everything in the company. Only an ADMIN whose `departmentRole` is `MANAGER` may register users. Deletes offices, locations and users. |
| `MANAGER` | Station manager | Admin-like, limited to their own office: dashboards, rider tracking, closing the counter day, cash-up adjustments, driver payouts and reversals, own price table, pickup charge. |
| `FRONTDESK` | Station counter staff | Parcel intake, search, assignment to riders, counter pickups, reconciliation, driver records and trips, fuel requests. |
| `RIDER` | Delivery rider | Own assignments, mark picked up / delivered / returned, own status, fuel requests, own reconciliations. |
| `CALLCENTER` | Call centre | Uncalled parcels, record call outcomes, call-centre stats. |
| `VENDOR` | Partner / online shop | Submit and edit own parcels, see own dashboard and earnings. Has no office; identified by phone number (`Parcel.vendorId`). |
| `CUSTOMER` | Declared only | Not used anywhere. Customers use the public portal without an account. |

`DepartmentRole`: `PARCELHUB`, `DELIVERYHUB`, `MANAGER`. Required when registering a user.

---

## 8. Business flows

### 8.1 Company registration

1. `POST /api-company/register` with `companyName`, `registerUserName`, `workEmail`, `phoneNumber` (must start with `+`), `password`, optional `plan`. Name, email and phone must be unique across companies and users.
2. The company is saved with `enabled=false`; an ADMIN user (`departmentRole=MANAGER`, `availability=false`) is created; an `EmailVerificationToken` is saved in `email_verification_tokens`; the verification email is sent asynchronously through Mailtrap.
3. `POST /api-company/verify-email` with the token. Rejected if used, expired, or the company is already enabled. On success the admin becomes available and the company is enabled.
4. `POST /api-company/resend-verification` with the email invalidates the latest token and sends a new one.

`EmailVerificationToken` (company signup) is unrelated to `VerificationToken` (`verification_tokens`, SMS OTP for password resets).

### 8.2 Staff onboarding

`POST /api-admin/register` (or the legacy `POST /api-user/admin/register`). Body: `name`, `email`, `phoneNumber` (with country code), `role`, `departmentRole`, `officeIds`.

- Every role except `VENDOR` needs at least one `officeId`, and each office must belong to the admin's company.
- A `MANAGER` is set as the manager of their offices.
- A `RIDER` gets a `rider_status` record set to `OFFLINE`.
- A random password is generated and sent by SMS (and email when present).

### 8.3 Offices, locations and shelves

- **Location**: a region/area (`name`, `region`, `country`). `POST /api-admin/location`.
- **Office** (also called a station): belongs to a location; has coordinates, opening hours, optional pickup charge, a driver pay policy and a manager. `POST /api-admin/office`.
- **Shelf**: a named shelf in an office. `POST /api-admin/shelf`.

### 8.4 Parcel intake

`POST /api-frontdesk/parcel` (FRONTDESK, MANAGER, ADMIN, VENDOR). Key behaviour:

- **Barcode**: a supplied `barCode` must be unique (400 otherwise); if blank, `BarcodeGenerator` assigns `PARCEL-<year>-<seq>`.
- **Images**: base64 strings in `images` are uploaded to R2; the public URLs go into `Parcel.imageUrls`.
- **Pins**: receiver and pickup coordinates are validated (both or neither; Ghana bounds unless disabled).
- **Pricing**: a home delivery with a pin is priced on the server from the station's table. If the charged `deliveryCost` differs from the server's suggestion, a `priceOverrideReason` is required (`OTHER` also needs `specialInstructions`).
- **Pickup requests** with no `pickUpCost` use the station's pickup charge when it is enabled.
- **Driver fee**: when `inboundCost > 0`, a `Driver` record is found (by normalised phone) or created, the parcel is attached to the driver's open trip for that vehicle and day, and a `DriverAssignment` fee line is created. A `DriverReconcilation` record is also updated.
- **Parcel types** (`typeofParcel`): `PARCEL`, `ONLINE`, `PICKUP`, `TRANSFER`. Flags: `isPOD` (payment on delivery: `itemCost` is collected from the receiver), `homeDelivery`, `isFragile`, `parcelTransfer`.
- `POST /api-frontdesk/parcels/received` marks a batch as received at the station.
- Optional receiver SMS with a `/receive` link when `RECEIVER_ARRIVAL_SMS=true`.

`PUT /api-frontdesk/parcel/{id}` updates a parcel. For the drop-off pin, an absent field means "leave as is" and an explicit `null` means "clear". A changed pin is copied onto open assignments and marks their route plan `stale`.

### 8.5 Rider assignment and delivery

**Assign** (`POST /api-frontdesk/assign-parcels`, `{ "riderId": "...", "parcelIds": ["..."] }`, optional `Idempotency-Key`):

- All parcels for a rider on one day share one assignment with id `{riderId}_{YYYYMMDD}`.
- A parcel can be on only one active (`ASSIGNED`, `ACCEPTED`, `PICKED_UP`) assignment. The same rider again → 409. Another rider's assignment loses the parcel. Delivered, collected or returned parcels → 409.
- Runs in a transaction; each parcel is claimed with a conditional update on `activeAssignmentId`.
- An optional route plan is validated and stored; the rider gets an SMS.

**Rider updates** (`PUT /api-rider/assignments/{assignmentId}/status`, body `status`, `parcelId`, optional `payementMethod`, `returnReason`):

- Riders may only set `PICKED_UP`, `DELIVERED` or `RETURNED`. They do not accept or decline; only a manager can reassign or cancel (`PUT /api-rider/manager/assignments/{id}/status`).
- `DELIVERED` marks the parcel delivered (`parcelStatus=DELIVERD`), releases its active-assignment claim, captures the rider's GPS position on the `ParcelInfo` (GPS failure never blocks the update), and texts the driver. When every parcel is delivered the assignment becomes `DELIVERED`.
- `RETURNED` records the reason and increments the parcel's `returnCount`.
- The confirmation-code check on delivery is currently commented out.

**Remove a parcel** from an assignment: `DELETE /api-frontdesk/assignment/{assignmentId}/parcel/{parcelId}`. An emptied assignment becomes `CANCELLED`.

**Duplicate repair**: `GET /api-admin/assignments/duplicates` lists parcels on more than one active assignment; `POST .../fix` keeps each on its newest assignment. Safe to re-run.

### 8.6 Rider reconciliation

`POST /api-frontdesk/reconcilation-parcels` records what a rider handed in against what the system expected, per parcel (`ReconciliationLine`: system amount, collected amount, variance, reason). Reconciliations are grouped per rider per day (`{riderId}_{YYYYMMDD}`) and stored in `reconcilations`. Reconciling home deliveries marks their driver fee lines READY.

### 8.7 Counter pickup and daily cash-up

**Pickup** (`POST /api-frontdesk/parcel/picked-up`):

```json
{ "parcelId": "...", "whoPickedUpName": "Ama", "whoPickedUpTelephoneNumber": "+233...",
  "amountDue": 25.00, "amountPaid": 25.00, "paymentMethod": "CASH" }
```

- The server recomputes amount due = inbound + storage + pickup fee + POD item value (delivery cost is not charged). A sent `amountDue` more than GHC 0.01 off → 400.
- `paymentMethod` is required when `amountPaid > 0`; `MOMO` needs `momoReference`.
- If paid ≠ due, a `varianceReason` is required (`Other` also needs `varianceNote`).
- One `CounterCollection` per parcel; a repeat → 409. The driver fee line becomes READY.
- Old clients that send no `amountPaid` still work (no collection recorded).

**Cash-up** (MANAGER closes the day): `POST /api-frontdesk/counter-cashup`. Days are station-local (`Africa/Accra`).

- Expected cash = counter cash − driver cash payouts − driver cash advances (− POD cash payouts).
- A closed day is never edited. Corrections go through `POST /api-frontdesk/counter-cashup/adjustments`. Money taken after closing goes to the next open day.

**POD payouts**: `GET /api-admin/pod-payouts` lists POD values collected but not yet paid to senders; `POST /api-admin/pod-payouts` records payment (all or nothing; 409 if any is already paid out). Cash payouts come out of that day's drawer.

### 8.8 Driver payments

A **driver** here is the person who brings parcels into a station (inbound), not a rider. Drivers are matched by phone normalised with `PhoneNumberUtils.normalizeGhana`.

- `DriverAssignment` (collection `driverAssignment`) is the **fee line**: one per parcel with `amount = inboundCost`.
- Fee line `state`: `WAITING → READY → PAID`, or `ON_HOLD` / `NOT_PAYABLE`. The old `payed` flag is kept equal to `state == PAID`.
- READY happens on counter pickup or rider reconciliation, or at intake if the station's `driverPayPolicy` is `ON_ARRIVAL` (default `ON_COLLECTION`).
- NOT_PAYABLE happens when the parcel is deleted or its status is set to `REVERSED`.
- **Trips** (`driver_trips`) group a driver's arrivals per vehicle and day, with declared vs received counts.
- **Adjustments** (`driver_adjustments`): `ADVANCE`, `DEDUCTION`, `BONUS`, `ADVANCE_REPAID`.
- **Payout** (`POST /api-frontdesk/driver-payouts`, MANAGER/ADMIN, `Idempotency-Key` supported): every line must be READY, belong to the driver and have no payout; the net must equal what staff saw (`expectedNetAmount`). Lines are claimed READY → PAID with a conditional update. A receipt number comes from the `counters` sequence.
- **Confirmation**: `POST .../{id}/send-code` texts the driver a 4-digit code; `PUT .../{id}/confirm` checks it.
- **Reversal** (`POST .../{id}/reverse`): lines return to READY, used adjustments reopen, cash goes back into the counter. Nothing is deleted.
- **Migration**: run `POST /api-admin/driver-payments/migrate` once after deploying, so legacy fee lines get a driver and a state.
- **Nightly integrity check** (see [section 14](#14-scheduled-jobs-async-work-and-indexes)).

`Parcel.driverRecordId` links to `Driver`. The older `Parcel.driverId` is unrelated.

### 8.9 Delivery pricing

Each station can have a price table (`delivery_pricing_tables`). Every save creates a new version.

Price rules (`DeliveryPricingRules`, mirrors the frontend's `deliveryPricing.ts`):

1. **Zone**: if the pin is inside one or more zones, the smallest zone's price applies.
2. Otherwise **distance**, rounded to 0.1 km:
   - `BANDS` mode: the first band with `distance ≤ upToKm`; beyond the last band, last price + `perKmBeyond` per extra km (rounded up).
   - `PER_KM` mode: `max(minFee, baseFee + perKm × km)`.
3. Plus the **weight** surcharge of the highest band with `weight > overKg` (bands do not add up).
4. Plus `fragileFee` if fragile.
5. Round the total **up** to a multiple of `roundTo`.

Several parcels in one trip cost the dearest single price + `extraParcelFee` per extra parcel.

Distance comes from the Google Routes API (`TWO_WHEELER`, then `DRIVE`), cached in `distance_cache` per station and pin rounded to about 55 m. Without a key or on failure it is the straight line × 1.35 (`ESTIMATE`).

Endpoints: `GET/PUT /api-frontdesk/office/delivery-pricing` (own table; MANAGER saves), `POST /api-frontdesk/delivery-pricing/quote`, `GET/PUT /api-admin/offices/{officeId}/delivery-pricing`, `GET /api-admin/delivery-pricing/review`.

### 8.10 Customer self-service portal (public)

Used by the frontend's `/receive` page. Returns `CustomerViews` only, never staff data.

1. **Find parcels**: `GET /api-user/parcel-search?phoneNumber=…` or open the SMS link (`GET /api-user/customer-links/{token}`). Receiver numbers are matched in `+233…`, `0…` and `233…` forms.
2. **Prove the phone**: `POST /api-user/customer-otp/send` (6 digits, 5 minutes, at most 3 per phone per 15 minutes, 404 if the phone has no parcels), then `POST /api-user/customer-otp/verify` (locked after 5 wrong tries). Returns a session token valid for a few hours. Only a hash of each token is stored (`customer_access`, TTL index).
3. **Quote**: `POST /api-user/delivery-requests/quote`. Valid 30 minutes. 401 token/phone mismatch, 403 not their parcel, 409 finished, with a rider or already booked. All parcels must be at one station.
4. **Book**: `POST /api-user/delivery-requests` with the Paystack reference. The server verifies the payment with Paystack (success, GHS, at least the quoted total, reference used once), then marks the parcels for home delivery as prepaid. If it cannot apply after payment, the request is stored as `FLAGGED` for the station.

### 8.11 Vendors / partners

- Public signup: `POST /api-vendor/applications` (at most 3 per phone per day; the company is chosen from the request's `Origin` domain). Admins review via `GET /api-admin/partner-applications`.
- VENDOR users submit parcels with `POST /api-vendor/parcels` or `POST /api-vendor/parcels/batch` (all or nothing; a failure names the parcel, e.g. `Parcel 2 (Kofi Owusu): …`). Both accept `Idempotency-Key`.
- A vendor may edit a parcel only while it is `PENDING` (not yet received).
- Dashboards: `GET /api-vendor/parcel-status-dashboard`, `GET /api-vendor/earnings`.

### 8.12 Transfers between offices

`TRANSFER` parcels carry `fromOfficeId`/`toOfficeId`. Front desk lists outgoing (`/parcels/transfer/outgoing`) and in-transit (`/parcels/transfer/in-transit`) transfers and marks arrival with `PUT /api-frontdesk/parcels/transfer/{parcelId}/arrived`.

### 8.13 Call centre

Follows up on parcels. `GET /api-call-center/parcels/uncalled`, `/delivered-uncalled`, `/not-delivered-uncalled`; `PUT /api-call-center/parcels/{parcelId}/call-outcome` sets `callOutCome` (`REACHED`, `UNREACHABLE`, `DELIVERED`), `hasCallCenterSpokenToClient` and a remark. Stats: `GET /api-call-center/stats`; per-caller stats for admins: `GET /api-admin/caller-stats`.

### 8.14 Fuel requests

Riders submit with `POST /api-rider/fuel-request` (`station`, `fuelStationNumber`, `attendantNumber`, `notes`, `tank`). Front desk/manager approve or reject with an amount via `PUT /api-frontdesk/fuel-request/{id}`. Status: `PENDING`, `APPROVED`, `REJECTED`.

### 8.15 GPS rider tracking and delivery checks

- A rider is linked to a GPS device through `User.deviceImei` (`PUT/DELETE /api-admin/riders/device`).
- Live location (`/api-admin/riders/location`, `/riders/locations`; up to 100 IMEIs per Jimi call) and track history (`/riders/track`). Riders without a device are listed with `located=false`.
- **Delivery check** (`/api-admin/riders/delivery-check`, `/summary`): `DeliveryCheckAnalyzer` compares the GPS track with drop-off pins, station coordinates and saved route plans to flag parcels marked done far from the pin, drop-offs never visited, long stops, GPS gaps, detours and off-task stops. Thresholds are in [section 3.3](#33-tuning-properties-no-env-var-in-applicationyml).
- Managers only see riders in their own office (403 otherwise).

### 8.16 Public tracking

`GET /api-tracking/{parcelId}/track` (no token) returns a customer view. `GET /api-tracking/{parcelId}` is a role-aware view for staff; `PATCH .../status` and `PATCH .../assign` update status or driver.

### 8.17 Audit log

Controller methods annotated `@TrackUserAction(action=…, description=…)` save a `UserAction` (user id, name, email in the misspelled `userEmai` field, office, company, action, description, time) to `user-actions`. Read with `GET /api-admin/user-actions`.

---

## 9. API reference

All paths are relative to `/shortly`. "Auth" lists the roles allowed by the service's `@PreAuthorize`; "token" means any logged-in user; "public" means no token. Swagger UI has request/response schemas for every endpoint.

### 9.1 `UserController`: `/api-user`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/login` | Log in, get JWT | public |
| POST | `/admin/register` | Register a user (legacy path of `/api-admin/register`) | ADMIN |
| POST | `/request-password-reset` | Text an OTP for password reset | public |
| POST | `/reset-password` | Reset password with verification id + code | public |
| PUT | `/profile` | Update own profile | token |
| POST | `/add-user-office` | Add an office to a user | ADMIN, MANAGER, FRONTDESK |
| GET | `/contacts` | Search contacts (paged; `name`, `phoneNumber`, `email`, `type`, `vehicleNumber`, `address`) | public (see §18) |
| GET | `/shelf/office/{id}` | Shelves of an office | public |
| GET | `/assignment/resend-confirmation-code/{id}` | Resend delivery code to receiver | public |
| GET | `/parcel-search?phoneNumber=` | Customer: open parcels for a phone | public |
| GET | `/customer-links/{token}` | Customer: parcels for an SMS link | public |
| POST | `/customer-otp/send` | Customer: text a one-time code | public |
| POST | `/customer-otp/verify` | Customer: check code, get session token | public |
| POST | `/delivery-requests/quote` | Customer: price a delivery | public (token in body) |
| POST | `/delivery-requests` | Customer: book a paid delivery | public (token in body) |
| GET | `/health` | Health check (`OK Ok`) | public |

### 9.2 `CompanyController`: `/api-company`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/register` | Register a company (disabled) and email a link | public |
| POST | `/verify-email` | Verify token, enable company and admin | public |
| POST | `/resend-verification` | New verification link | public |

### 9.3 `AdminController`: `/api-admin`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/register` | Register a user | ADMIN (department MANAGER) |
| POST | `/office` | Add an office | ADMIN, MANAGER |
| PUT | `/office/{id}` | Update an office | ADMIN, MANAGER |
| DELETE | `/office/{id}` | Delete an office | ADMIN |
| POST | `/location` | Add a location | ADMIN, MANAGER |
| PUT | `/location/{id}` | Update a location | ADMIN, MANAGER |
| DELETE | `/location/{id}` | Delete a location | ADMIN |
| POST | `/shelf` | Add a shelf | ADMIN, MANAGER |
| PUT | `/user/{userId}/status/{status}` | Change user availability | ADMIN |
| DELETE | `/user/{userId}` | Delete a user | ADMIN |
| GET | `/users` | List users | ADMIN, MANAGER |
| GET | `/parcels` | Search parcels | token |
| GET | `/reconciliations`, `/reconciliations/by-date` | Office reconciliations | ADMIN, MANAGER |
| GET | `/user-actions` | Audit log | ADMIN, MANAGER |
| GET | `/caller-stats` | Call-centre caller stats | ADMIN, MANAGER |
| GET | `/parcel-system-logs` | Pickup system logs | ADMIN, MANAGER |
| GET | `/dashboard` | Admin dashboard | ADMIN, MANAGER |
| GET | `/rider-performance-dashboard` | Rider performance | ADMIN, MANAGER |
| GET | `/revenue-dashboard` | Revenue analytics | ADMIN, MANAGER |
| GET | `/riders/location` | One rider's live location | ADMIN, MANAGER |
| GET | `/riders/locations` | All riders' locations | ADMIN, MANAGER |
| GET | `/riders/track` | Rider track history | ADMIN, MANAGER |
| PUT / DELETE | `/riders/device` | Assign / remove a GPS device | ADMIN, MANAGER |
| GET | `/riders/delivery-check` | Delivery and off-task check | ADMIN, MANAGER |
| GET | `/riders/delivery-check/summary` | Daily check summary | ADMIN, MANAGER |

### 9.4 `AdminFinanceController`: `/api-admin`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/counter-cashups` | Cash-ups by station and day | ADMIN, MANAGER |
| GET | `/pod-payouts` | POD values collected and still owed | ADMIN, MANAGER |
| POST | `/pod-payouts` | Record POD values paid to senders | ADMIN, MANAGER |
| GET | `/driver-payouts/summary` | Payouts per station and driver | ADMIN, MANAGER |
| GET | `/driver-trips/missing-parcels` | Trips with fewer parcels received than declared | ADMIN, MANAGER |
| GET | `/driver-fee-lines/long-waiting` | Fees READY for more than N days | ADMIN, MANAGER |
| POST | `/driver-payments/migrate` | One-off migration of legacy fee lines | ADMIN, SUPERADMIN |
| GET | `/driver-payments/integrity` | Problems found by the nightly check | ADMIN, SUPERADMIN, MANAGER |
| POST | `/driver-payments/integrity/run` | Run the check now | ADMIN, SUPERADMIN |
| GET | `/assignments/duplicates` | Parcels on more than one active assignment | ADMIN, MANAGER |
| POST | `/assignments/duplicates/fix` | Remove duplicates | ADMIN, MANAGER |
| GET | `/partner-applications` | Become-a-partner requests | ADMIN, SUPERADMIN |

### 9.5 `FrontDeskController`: `/api-frontdesk`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/parcel` | Add a parcel | FRONTDESK, MANAGER, ADMIN, VENDOR |
| PUT | `/parcel/{id}` | Update a parcel | token |
| DELETE | `/parcel/{parcelId}` | Delete a parcel | FRONTDESK, MANAGER, ADMIN, VENDOR |
| POST | `/parcels/received` | Mark parcels received | FRONTDESK, MANAGER, ADMIN |
| GET | `/parcels` | Search parcels | token |
| GET | `/parcels/home-delivery` | Home-delivery parcels | FRONTDESK, MANAGER, ADMIN, VENDOR |
| GET | `/parcels/delivered` | Delivered parcels | token |
| GET | `/parcels-uncalled` | Uncalled parcels | ADMIN, MANAGER, CALLCENTER |
| POST | `/parcels/{parcelId}/follow-up` | Record a follow-up call | token |
| POST | `/parcel/picked-up` | Counter pickup with payment | FRONTDESK, MANAGER, ADMIN |
| POST | `/pickup-request` | Create a pickup request | token |
| POST | `/assign-parcels` | Assign parcels to a rider (`Idempotency-Key`) | FRONTDESK, MANAGER, ADMIN |
| GET | `/parcel-assignment` | Assignments by status | FRONTDESK, MANAGER, ADMIN |
| DELETE | `/assignment/{assignmentId}/parcel/{parcelId}` | Remove parcel from assignment | MANAGER, ADMIN |
| GET | `/rider/{riderId}/assignments` | A rider's assignments | FRONTDESK, MANAGER, ADMIN |
| GET | `/riders/office` | Riders in the office | MANAGER, FRONTDESK |
| GET | `/riders/assignments` | Office rider assignments | token |
| GET | `/riders/assignments/returned` | Returned assignments | token |
| POST | `/reconcilation-parcels` | Reconcile rider money | FRONTDESK, MANAGER, ADMIN |
| GET | `/reconciliation/stats` | Reconciliation stats | token |
| GET | `/reconciliations/by-date` | Reconciliations by date | MANAGER, ADMIN |
| GET | `/driver/{driverId}/parcels` | Parcels of a driver | token |
| GET | `/driver-reconciliations/unpaid` | Unpaid driver reconciliations | FRONTDESK, ADMIN, MANAGER |
| GET | `/driver-assignments/unpaid` | Unpaid driver fee lines (legacy) | FRONTDESK, MANAGER, ADMIN |
| PUT | `/driver-assignments/pay` | Pay fee lines (legacy, cash, no adjustments) | FRONTDESK, MANAGER, ADMIN |
| PUT | `/office/pickup-charge` | Set own station's pickup charge | MANAGER |
| GET / POST | `/addresses` | Search / save addresses | token |
| GET | `/online-parcels/unpaid` | Unpaid online parcels | FRONTDESK, MANAGER, ADMIN, VENDOR |
| GET | `/parcels/online/arrived` | Online parcels arrived | FRONTDESK, MANAGER, ADMIN, VENDOR |
| GET | `/parcels/transfer/outgoing` | Outgoing transfers | FRONTDESK, MANAGER, ADMIN, VENDOR |
| GET | `/parcels/transfer/in-transit` | Transfers in transit | FRONTDESK, MANAGER, ADMIN, VENDOR |
| PUT | `/parcels/transfer/{parcelId}/arrived` | Mark transfer arrived | FRONTDESK, MANAGER, ADMIN |
| GET | `/fuel-requests` | Fuel requests | FRONTDESK, MANAGER, ADMIN |
| GET | `/fuel-request/stats` | Fuel request stats | FRONTDESK, MANAGER, ADMIN |
| PUT | `/fuel-request/{fuelRequestId}` | Approve / reject | FRONTDESK, MANAGER, ADMIN |

### 9.6 `CounterController`: `/api-frontdesk`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/counter-collections` | Counter pickups for a day | FRONTDESK, MANAGER, ADMIN |
| GET | `/counter-cash-movements` | Everything through the drawer for a day | FRONTDESK, MANAGER, ADMIN |
| POST | `/counter-cashup` | Close a counter day | MANAGER |
| GET | `/counter-cashup` | A closed day | MANAGER, ADMIN |
| POST | `/counter-cashup/adjustments` | Correct a closed day | MANAGER |

### 9.7 `DriverPaymentController`: `/api-frontdesk`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET / POST | `/drivers` | Search / create drivers | FRONTDESK, MANAGER, ADMIN |
| PUT | `/drivers/{id}` | Update a driver | FRONTDESK, MANAGER, ADMIN |
| GET | `/drivers/{id}/statement` | Driver statement | FRONTDESK, MANAGER, ADMIN |
| GET / POST | `/drivers/{id}/adjustments` | List / add advance, deduction, bonus | list: FRONTDESK+; add: MANAGER, ADMIN |
| GET / POST | `/driver-trips` | List / open a trip | FRONTDESK, MANAGER, ADMIN |
| GET | `/driver-balances` | Balances per driver | FRONTDESK, MANAGER, ADMIN |
| GET | `/driver-payments/summary` | Ready, waiting, paid this week, on hold | FRONTDESK, MANAGER, ADMIN |
| PUT | `/driver-fee-lines/{id}/hold`, `/release` | Hold / release a fee line | FRONTDESK, MANAGER, ADMIN |
| POST | `/driver-payouts` | Pay a driver (`Idempotency-Key`) | MANAGER, ADMIN |
| GET | `/driver-payouts`, `/driver-payouts/{id}` | List / receipt | FRONTDESK, MANAGER, ADMIN |
| POST | `/driver-payouts/{id}/send-code` | Text a 4-digit code | FRONTDESK, MANAGER, ADMIN |
| PUT | `/driver-payouts/{id}/confirm` | Confirm with code | FRONTDESK, MANAGER, ADMIN |
| POST | `/driver-payouts/{id}/reverse` | Reverse a payout | MANAGER, ADMIN |

### 9.8 `DeliveryPricingController`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/api-frontdesk/office/delivery-pricing` | Own station's table | FRONTDESK, MANAGER, ADMIN |
| PUT | `/api-frontdesk/office/delivery-pricing` | Save own table | MANAGER |
| POST | `/api-frontdesk/delivery-pricing/quote` | Price a pin | FRONTDESK, MANAGER, ADMIN |
| GET / PUT | `/api-admin/offices/{officeId}/delivery-pricing` | Any station's table | ADMIN, SUPERADMIN |
| GET | `/api-admin/delivery-pricing/review` | Pricing review per band and zone | ADMIN, SUPERADMIN |

### 9.9 `RiderController`: `/api-rider`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| PUT | `/rider-status` | Set own status (`READY`, `OFFLINE`, `BUSY`, `ON_TRIP`) | RIDER |
| GET | `/assignments` | Own assignments | token (rider) |
| PUT | `/assignments/{assignmentId}/status` | Mark parcel picked up / delivered / returned | token (assigned rider) |
| PUT | `/assignments/update` | Update a delivery assignment | RIDER, MANAGER, ADMIN |
| PUT | `/manager/assignments/{assignmentId}/status` | Manager status override | ADMIN, MANAGER |
| GET | `/search` | Search own assignments by receiver phone | token (rider) |
| GET | `/reconciliations` | Own reconciliations | RIDER |
| GET | `/fuel-requests` | Own fuel requests | RIDER |
| POST | `/fuel-request` | Submit a fuel request | RIDER |

### 9.10 `CallCenterController`: `/api-call-center`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/parcels/uncalled` | Parcels not yet called | ADMIN, MANAGER, CALLCENTER |
| GET | `/parcels/delivered-uncalled` | Delivered, not called | ADMIN, MANAGER, CALLCENTER |
| GET | `/parcels/not-delivered-uncalled` | Not delivered, not called | ADMIN, MANAGER, CALLCENTER |
| PUT | `/parcels/{parcelId}/call-outcome` | Record call outcome | ADMIN, MANAGER, CALLCENTER |
| GET | `/stats` | Call-centre stats | token |

### 9.11 `VendorController`: `/api-vendor`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/applications` | Become a partner | public |
| GET | `/parcels` | Own parcels | VENDOR |
| POST | `/parcels` | Submit a parcel (`Idempotency-Key`) | VENDOR |
| POST | `/parcels/batch` | Submit several parcels, all or nothing (`Idempotency-Key`) | VENDOR |
| GET | `/parcel-status-dashboard` | Status dashboard | VENDOR |
| GET | `/earnings` | Earnings dashboard | VENDOR |

### 9.12 `TrackingController`: `/api-tracking`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/{parcelId}/track` | Public tracking (customer view) | public |
| GET | `/{parcelId}` | Role-aware parcel view | token |
| PATCH | `/{parcelId}/status` | Set `parcelStatus` (`REVERSED` makes the fee NOT_PAYABLE) | RIDER, ADMIN, MANAGER |
| PATCH | `/{parcelId}/assign` | Assign a driver to a parcel | FRONTDESK, ADMIN, MANAGER |

### 9.13 `OfficeController`: `/api/offices`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/locations` | All locations with their offices | token |
| GET | `/locations/{id}` | One location | token |

`ReceiverController` exists but has no endpoints.

---

## 10. Data model and MongoDB collections

Collection names come from `@Document` and must not be renamed: several contain typos and already hold production data.

### 10.1 Collections

| Collection | Model | Contents |
|---|---|---|
| `users` | `User` | Staff and vendor accounts: `name`, `phoneNumber` (username), `email`, `passwordHash`, `role`, `departmentRole`, `companyId`, `officeIds`, `availability`, `status`, `deviceImei` |
| `companies` | `Company` | Tenant: names, branding colours, manager, contact, `emailVerified`, `enabled`, `plan` |
| `email_verification_tokens` | `EmailVerificationToken` | Company signup links |
| `verification_tokens` | `VerificationToken` | Password-reset OTPs (5 min) |
| `locations` | `Location` | Regions/areas |
| `offices` | `Office` | Stations: coordinates, pickup charge, opening hours, embedded `Location` and manager `User` |
| `shelfs` | `Shelf` | Shelves per office |
| `parcelss` | `Parcel` | The parcel (double s) |
| `delivery_assignmentsss` | `DeliveryAssignments` | Rider assignments (triple s): rider snapshot, embedded `ParcelInfo` list, status, money, route plan, reconciliation lines |
| `driverAssignment` | `DriverAssignment` | Driver fee lines (no `@Document`; default name) |
| `drivers` | `Driver` | Inbound drivers: phones, MoMo, vehicles, status |
| `driver_trips` | `DriverTrip` | Driver arrivals with declared/received counts |
| `driver_payouts` | `DriverPayout` | Payouts with receipt no., lines, adjustments, confirmation, reversal |
| `driver_adjustments` | `DriverAdjustment` | Advances, deductions, bonuses |
| `driver_payment_issues` | `DriverPaymentIssue` | Nightly integrity-check findings |
| `driver_reconcilations` | `DriverReconcilation` | Legacy inbound cost per rider |
| `reconcilations` | `Reconcilations` | Rider reconciliations |
| `counter_collections` | `CounterCollection` | One per parcel collected at the counter |
| `counter_cashups` | `CounterCashup` | Closed counter days with appended adjustments |
| `counters` | `Counter` | Sequences (barcodes `parcel_<year>`, payout receipt numbers) |
| `rider_status` | `RiderStatusModel` | Rider availability |
| `fuel_requests` | `FuelRequest` | Rider fuel requests |
| `contacts` | `Contacts` | Address book of drivers, receivers, senders |
| `called_parcel_infos` | `CalledParcelInfo` | Follow-up call records |
| `parcel_system_logs` | `ParcelSystemLog` | Who picked up a parcel, when, and who served them |
| `user-actions` | `UserAction` | Audit log |
| `jimi_access_tokens` | `JimiAccessToken` | Cached Jimi OAuth token (single doc id `jimi-token`) |
| `idempotency_keys` | `IdempotencyRecord` | Idempotency results (24 h TTL) |
| `customer_access` | `CustomerAccess` | Hashed customer link/OTP/session tokens (TTL on `expiresAt`) |
| `customer_delivery_requests` | `CustomerDeliveryRequest` | Paid customer bookings |
| `delivery_pricing_tables` | `DeliveryPricingTable` | Versioned station price tables |
| `delivery_quotes` | `DeliveryQuote` | Quotes (kept 30 days) |
| `distance_cache` | `DistanceCacheEntry` | Cached road distances |
| `vendor_applications` | `VendorApplication` | Partner signup requests |

`Address` is stored through `AddressRepository` (front-desk saved addresses).

### 10.2 Embedded value objects

| Class | Used in | Holds |
|---|---|---|
| `ParcelInfo` | assignments, fee lines, reconciliations, logs | Parcel snapshot incl. pins, money, flags, and `marked*` GPS fields captured on delivery |
| `RiderInfo` | assignments, parcels, fuel requests, reconciliations | `riderId`, `riderName`, `riderPhoneNumber`, `stationName` |
| `OfficeInfo` | parcel `from` / `to` | `officeId`, `officeName` |
| `RoutePlan` | assignments | Stop order, km, minutes, legs, encoded path, `stale` |
| `ReconciliationLine` | assignments | Per-parcel system vs collected amount |
| `CounterPayment`, `CounterCharge` | parcels, logs, collections | Counter payment and charge breakdown |

### 10.3 Important `Parcel` fields

| Group | Fields |
|---|---|
| Identity | `parcelId`, `barCode` (unique, sparse), `companyId`, `officeId`, `typeofParcel`, `parcelStatus` |
| People | `senderName/PhoneNumber`, `receiverName`, `recieverPhoneNumber` (sic), `alternativePhoneNumber`, `driverName/PhoneNumber`, `vehicleNumber` |
| Pins | `receiverLatitude/Longitude/LocationName/PlaceId`, `pickupLatitude/Longitude/LocationName/PlaceId` |
| Money | `inboundCost` (driver fee), `deliveryCost`, `storageCost`, `pickUpCost`, `ItemCost` (POD value), `vendorDeliveryFee`, `deliveryPrepaidAmount` |
| Flags | `isPOD`, `isFragile`, `homeDelivery`, `isDelivered`, `isParcelAssigned`, `pickedUp`, `hasCalled`, `inboudPayed` (sic), `podCollected` |
| Assignment | `activeAssignmentId`, `riderId`, `riderInfo`, `returnCount` |
| Driver payments | `driverRecordId` (→ `Driver`), `tripId`. `driverId` is an older unrelated field |
| Pricing | `distanceKm`, `distanceSource`, `suggestedDeliveryCost`, `pricingTableVersion`, `pricingZone`, `pricingBucketKey` |
| Transfer | `from`, `to`, `fromOfficeId`, `toOfficeId`, `hasArrivedAtOffice`, `parcelTransfer` |
| Vendor | `vendorId` (phone), `vendorName`, `isVendorPayed`, `numberOfItems`, `itemQuantity` |
| Call centre | `callerName`, `callerPhoneNumber`, `notes`, `hasCallCenterSpokenToClient`, `callOutCome`, `callCenterRemark` |
| Images | `imageUrls` |

**JSON naming quirk:** a Lombok boolean named `isX` serialises as `X` (the POD flag is sent back as `"POD"`). `ParcelRequest`/`VendorParcelRequest` accept `POD`, `pod` and `isPOD`.

---

## 11. Enums

| Enum | Values |
|---|---|
| `UserRole` | SUPERADMIN, ADMIN, RIDER, FRONTDESK, MANAGER, CALLCENTER, VENDOR, CUSTOMER |
| `DepartmentRole` | PARCELHUB, DELIVERYHUB, MANAGER |
| `UserStatusEnum` | ACTIVE, INACTIVE, SUSPENDED, DELETED |
| `ParcelTypes` | PARCEL, ONLINE, PICKUP, TRANSFER |
| `ParcelStatus` | RECEIVED, PENDING, DELIVERD (sic), COLLECTED, FAILED, REVERSED |
| `DeliveryStatus` | ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED, RETURNED, COMPLETED, CANCELLED |
| `RiderStatus` | READY, OFFLINE, BUSY, ON_TRIP |
| `DriverFeeState` | WAITING, READY, PAID, ON_HOLD, NOT_PAYABLE |
| `DriverPayPolicy` | ON_COLLECTION, ON_ARRIVAL |
| `DriverAdjustmentType` | ADVANCE, DEDUCTION, BONUS, ADVANCE_REPAID |
| `DriverStatus` | ACTIVE, BLOCKED |
| `CounterPaymentMethod` | CASH, MOMO |
| `MomoNetwork` | MTN, TELECEL, AIRTELTIGO |
| `CallCenterCallOutCome` | REACHED, UNREACHABLE, DELIVERED |
| `ContactType` | DRIVER, RECEIVER, SENDER |
| `FuelRequestStatus` | PENDING, APPROVED, REJECTED |
| `LocationSource` | SEARCH, MAP_PIN |
| `PriceOverrideReason` | BULKY, HARD_TO_REACH, REGULAR_CUSTOMER, PROMOTION, WRONG_PIN, OTHER |
| `ReconcilationType` | DRIVER, RIDER |
| `CompanyRole` | ALL, PARCELMANAGER, DELIVERYMANAGER |

Enum values in requests are case-insensitive.

---

## 12. External integrations

All outbound HTTP uses `WebClient`.

| Service | Class | How it is called | Failure behaviour |
|---|---|---|---|
| **MNotify** (SMS) | `SMSNotification` | `POST {baseUrl}/quick?key={MNOTIFY_API}` with `recipient`, `sender`, `message`; `sms_type=otp` for OTPs. `@Async`. | Logged, never thrown |
| **Mailtrap** (email) | `EmailServiceImplementation` | REST `POST` to `MAILTRAP_API_URL` with bearer token. Sends company verification and new-user credential emails. | Logged |
| **Cloudflare R2** (images) | `R2Service`, `R2Config` | AWS SDK S3 client, endpoint `https://<account>.r2.cloudflarestorage.com`, region `auto`, path-style. Public URL = `R2_PUBLIC_BASE_URL/<key>`. | Upload error fails parcel creation |
| **Jimi / TrackSolid Pro** (GPS) | `JimiTokenService`, `JimiApiService` | Form-encoded POST, MD5-signed params. `jimi.oauth.token.get` for a token (cached in Mongo, refreshed 5 min early); then `jimi.device.location.get` / `jimi.device.track.list`. Always go through the token service. | 502 to the caller; delivery status updates never blocked |
| **Google Routes** | `RoadDistanceService` | `POST https://routes.googleapis.com/directions/v2:computeRoutes`, 6 s timeout, cached. | Falls back to straight line × 1.35 |
| **Paystack** | `CustomerPortalService` | `GET https://api.paystack.co/transaction/verify/{reference}`, 15 s timeout. | Booking refused, or stored as FLAGGED if payment was taken |

### SMS messages sent

| When | Recipient |
|---|---|
| New staff account (credentials) | New user |
| Password reset OTP | User |
| Parcels assigned | Rider |
| Assignment details / confirmation code | Receiver |
| Parcel delivered / picked up (with fee) | Inbound driver |
| Parcel received at hub | Driver |
| Fuel request submitted | Station |
| Driver payout confirmation code | Driver |
| Customer OTP and arrival link (if enabled) | Receiver |

---

## 13. Error handling

`GlobalExceptionHandler` returns JSON `ErrorResponse`:

```json
{ "message": "A user with this phone number already exists", "data": null }
```

| Exception | Status |
|---|---|
| `MethodArgumentNotValidException` (bean validation) | 400, field errors in `data` |
| `HttpMessageNotReadableException` (bad JSON, wrong type, bad enum) | 400 |
| `EntityAlreadyExist`, `DuplicateKeyException` | 409 |
| `IncorrectResultSizeDataAccessException` (duplicate data where one was expected) | 409 |
| `ActionNotAllowed` | 409 |
| `EntityNotFound` | **409** (not 404) |
| `WrongCredentialsException` | 401 |
| `ExpiredJwtException` | 401 |
| `NoHandlerFoundException` | 404 |
| `ResponseStatusException` | its own status and reason |
| `@PreAuthorize` denial | 403 |

Other exception classes: `UnAthorizeException` (sic), `InsufficientPrivilegesException`, `UserNotFoundException`.

---

## 14. Scheduled jobs, async work and indexes

### Scheduled

| Job | Schedule | What it does |
|---|---|---|
| `DriverPaymentIntegrityService.nightly` | `shortly.integrity.cron` (default 02:30 Africa/Accra) | Checks every company: each PAID line has a live payout; payout gross = its lines; net = gross − deductions + bonuses; no line on two live payouts; advance `repaidAmount` matches repayments. Replaces the company's rows in `driver_payment_issues`. |

### Async

SMS and email go through the `taskExecutor` pool (`AsyncConfig`), so a slow provider never blocks a request.

### Indexes

`spring.data.mongodb.auto-index-creation` is off, so `@Indexed`/`@CompoundIndex` create nothing on their own. `IndexInitializer` runs at startup and creates the indexes the newer features need; a failure is logged and never stops startup:

- `counter_collections`: unique `parcelId`; `companyId+officeId+businessDate+pickedUpAt`
- `counter_cashups`: unique `officeId+date`
- `driverAssignment`: `driverId+state`, `officeId+state`, `payoutId`, `parcelId`, `tripId`
- `driver_trips`, `driver_payouts`, `driver_adjustments`, `drivers`: query indexes
- `parcelss`: sparse `activeAssignmentId`
- `idempotency_keys`: TTL 24 h on `createdAt`
- `customer_access`: `tokenHash`, TTL on `expiresAt`, `phoneNumber+kind+createdAt`
- `delivery_pricing_tables`: unique `officeId+version`
- `delivery_quotes`: TTL 30 days on `expiresAt`
- `distance_cache`: unique `key`
- `customer_delivery_requests`: unique sparse `paystackReference`
- `vendor_applications`: `phoneNumber+createdAt`

Older indexes on `Parcel` (`office_delivered_idx`, `office_homedelivery_idx`, `office_called_idx`, `search_parcels_idx`) and `DeliveryAssignments` (`rider_status_idx`, `office_status_idx`, `office_payed_idx`, `rider_phone_status_idx`, `office_assigned_idx`) are declared on the models and must already exist in the database.

---

## 15. Testing

Tests are in `src/test/java/shortly/mandmcorp/dev/shortly/`. Most are pure unit tests of the rule classes and need no database:

| Test | Covers |
|---|---|
| `DeliveryCheckAnalyzerTest` | GPS delivery / off-task rules |
| `DeliveryPricingRulesTest` | Zones, bands, per-km, weight, fragile, rounding |
| `CounterPickupRulesTest` | Amount due and payment validation |
| `ReconciliationValidatorTest` | Reconciliation lines |
| `DriverPaymentRangeTest` | Date ranges for driver payments |
| `CustomerPortalPhoneTest` | Phone number variants |
| `PhoneNumberUtilsTest`, `MoneyTest`, `GeoUtilsTest` | Helpers |
| `RequestBindingTest` | JSON binding quirks (`POD`/`isPOD`, missing primitives) |
| `ShortlyApplicationTests` | Spring context load (needs all required env vars and a database) |

```bash
./mvnw test
./mvnw test -Dtest=DeliveryPricingRulesTest
```

---

## 16. Build, Docker and deployment

### Docker

The [Dockerfile](Dockerfile) is a two-stage build: `maven:3.9-eclipse-temurin-21` runs `mvn clean package -DskipTests`, then `eclipse-temurin:21-jdk` runs `java -jar app.jar` on port 8080.

```bash
docker build -t shortly-backend .
docker run -p 8080:8080 --env-file .env shortly-backend
```

### CI/CD (GitHub Actions)

Both workflows build the image, push it to Amazon ECR with tag `latest`, and start an Auto Scaling Group instance refresh (min healthy 90 %, warm-up 300 s):

| Workflow | Trigger | ECR repository | ASG |
|---|---|---|---|
| `build-and-push-to-ecr.yml` | push to `test` | `mandm/parcel-automation-backend` | `parcel-automation-backend-servers` |
| `new-accout.yaml` | push to `v2` | `mnm/parcel-automation-backend` | `mnm-parcel-servers` |

The EC2 instances must provide the environment variables to the container (the workflows do not pass them).

### EC2 bootstrap

`.sh` is an EC2 user-data script: installs Docker, logs in to ECR, pulls the image and runs it on port 80 with environment variables. See [section 18](#18-known-issues-and-gotchas) before using it.

### After deploying

- Run `POST /api-admin/driver-payments/migrate` once when moving an existing database to the driver-payments version.
- Check `GET /api-admin/assignments/duplicates` and fix if needed.

---

## 17. Root-level files

| File | What it is |
|---|---|
| `pom.xml` | Maven build. Lombok is in `annotationProcessorPaths` with an explicit `${lombok.version}`, which is required. |
| `lombok.config` | Copies `@Qualifier` onto Lombok-generated constructors. |
| `CLAUDE.md` | Notes for AI-assisted development. |
| `Dockerfile` | Container build. |
| `.sh` | EC2 user-data deploy script. |
| `.github/workflows/` | CI/CD to ECR + ASG. |
| `migrate_assignments.py` | One-off script that grouped old per-parcel assignments into the per-rider assignment format, matching riders by phone. |
| `MIGRATION_README.md`, `MIGRATION_QUICKSTART.md` | Notes for that migration. |
| `assignment.txt`, `migrated_assignments.json`, `migrated_assignments_final.json` | Input/output data of that migration. |
| `cors-test.html` | Browser page to test CORS against a deployed endpoint. |
| `.env.example` | Environment variable template. |

---

## 18. Known issues and gotchas

### Security

- **`.sh` contains live secrets in git.** It hard-codes a MongoDB connection string with password, the MNotify key and JWT keys. Rotate those credentials, remove the values from the script (pass them from a secret store or `--env-file`), and purge them from git history.
- **Everything under `/api-user/**` is public at the filter level.** Routes that rely on service checks are still protected (`/admin/register`, `/add-user-office`), but these have no check and work without a token: `GET /api-user/contacts` (contact book search), `GET /api-user/shelf/office/{id}`, and `GET /api-user/assignment/resend-confirmation-code/{id}` (sends an SMS). `PUT /api-user/profile` needs a token in practice because it reads the logged-in user.
- **CORS** accepts any origin with credentials.
- **Delivery confirmation code** is not checked when a rider marks a parcel delivered (the check is commented out).
- **Actuator** is on the classpath, but `/actuator/**` is not in the public list. Use `GET /api-user/health` for load-balancer health checks.

### Configuration

- `JWTConfig` reads `JWT_ACCESS_KEY`, `JWT_REFRESH_KEY`, `JWT_EXPIRATION_TIME`. The `jwt:` block in `application.yml` (with the `JWT_REFERESH_KEY` and `JWT_EXPIRATIONTIME` spellings) is not used.
- `frontend-server.baseUrl` in the yml is unused; `FrontEndServerConfig` binds `backend-server.baseUrl`, which is not set.
- Spring Boot does not load `.env`; export the variables.
- MongoDB must be a replica set for transactions.
- `RateLimitFilter` is fully commented out. Do not re-add `bucket4j_jdk17-core`: it uses `java.lang.foreign.Linker` (a preview API in Java 21) and breaks Lombok annotation processing. If the build fails with "java.lang.foreign.Linker is a preview API", look for a stray IDE auto-import (for example near the top of `ParcelServiceImplementation.java`).

### Naming typos to keep (data already stored this way)

| Typo | Where |
|---|---|
| `parcelss`, `delivery_assignmentsss`, `shelfs` | Collection names |
| `UnAthorizeException` | Exception class |
| `UserAction.userEmai` | Audit-log field |
| `FuelRequest.fuleStationPhoneNumber` | Fuel request field |
| `recieverPhoneNumber`, `inboudPayed`, `payementMethod`, `ParcelStatus.DELIVERD` | Parcel / assignment fields and enum |
| `reconcilations`, `driver_reconcilations` | Collection names |

### Other

- `GlobalExceptionHandler` maps `EntityNotFound` and `ActionNotAllowed` to 409, so many "not found" cases return 409 even where Swagger says 404. Code that throws `ResponseStatusException(NOT_FOUND)` does return 404.
- `CUSTOMER` role is declared but unused.
- `ReceiverController` and `MongoConfig` are empty.
- No barcode/QR scanning endpoint exists; barcodes are generated only.
- Embedded snapshots (`ParcelInfo`, `RiderInfo`, `OfficeInfo`) do not update when the source document changes.
