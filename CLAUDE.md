# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Technology Stack

- **Framework**: Spring Boot 4.0.0
- **Java Version**: 21
- **Build Tool**: Maven
- **Database**: MongoDB (with Spring Data MongoDB)
- **Security**: JWT-based authentication (jjwt 0.11.5)
- **API Documentation**: SpringDoc OpenAPI 2.7.0
- **Rate Limiting**: Bucket4j 8.15.0
- **SMS Notifications**: MNotify API
- **Libraries**: Lombok, Spring WebFlux, AspectJ, BCrypt

## Build and Development Commands

### Run the application
```bash
./mvnw spring-boot:run
```

### Build the project
```bash
./mvnw clean package
```

### Run tests
```bash
./mvnw test
```

### Run specific test class
```bash
./mvnw test -Dtest=ClassName
```

### Clean build artifacts
```bash
./mvnw clean
```

### Skip tests during build
```bash
./mvnw package -DskipTests
```

## Application Configuration

### Required environment variables (see .env)
- `MONGO_URL`: MongoDB connection string
- `MNOTIFY_API`: MNotify API key for SMS notifications
- `JWT_ACCESS_KEY`: Secret key for JWT access tokens
- `JWT_REFRESH_KEY`: Secret key for JWT refresh tokens (note: typo in application.yml as JWT_REFERESH_KEY)
- `JWT_EXPIRATIONTIME`: JWT expiration time in milliseconds
- `FRONTEND_HOST`: Frontend server URL for CORS

### Server configuration
- Base context path: `/shortly`
- All API endpoints are prefixed with `/shortly`
- Swagger UI available at: `/shortly/swagger-ui/index.html`
- API docs at: `/shortly/v3/api-docs`
- Health endpoint: `/shortly/health`

## Architecture Overview

This is a parcel and delivery management system with a standard Spring Boot layered architecture.

### Business Domain

The system manages parcels, deliveries, and logistics for a courier service with:
- **Multiple office locations** with shelves for parcel storage
- **User roles**: ADMIN, RIDER, FRONTDESK, MANAGER
- **Parcel types**: Regular parcels, home delivery, POD (payment on delivery), pickup requests
- **Delivery assignments**: Riders are assigned parcels for delivery
- **Reconciliation**: Financial reconciliation between riders and offices
- **Contact management**: Sender and receiver contact tracking
- **Notifications**: SMS and email notifications via MNotify API

### Layer Structure

**Controllers** (`controller/`)
- [AdminController.java](src/main/java/shortly/mandmcorp/dev/shortly/controller/AdminController.java) - Admin operations, reconciliation
- [FrontDeskController.java](src/main/java/shortly/mandmcorp/dev/shortly/controller/FrontDeskController.java) - Parcel registration, office operations
- [RiderController.java](src/main/java/shortly/mandmcorp/dev/shortly/controller/RiderController.java) - Delivery assignments, status updates
- [UserController.java](src/main/java/shortly/mandmcorp/dev/shortly/controller/UserController.java) - User authentication, registration, profile management
- [OfficeController.java](src/main/java/shortly/mandmcorp/dev/shortly/controller/OfficeController.java) - Office and location management
- [ReceiverController.java](src/main/java/shortly/mandmcorp/dev/shortly/controller/ReceiverController.java) - Receiver-specific operations

**Services** (`service/`)
Each service has an interface and implementation under `impl/`:
- `user/` - User management and authentication
- `parcel/` - Parcel operations and tracking
- `rider/` - Rider management and delivery assignments
- `office/` - Office and location management
- `contact/` - Contact information management
- `notification/` - Email and SMS notifications

**Repositories** (`repository/`)
Spring Data MongoDB repositories for data access:
- UserRepository, ParcelRepository, OfficeRepository, LocationRepository
- DeliveryAssignmentsRepository, ReconcilationRepository
- RiderStatusRepository, ShelfRepository, VerificationTokenRepository
- ContaceRepository, UserActionRepository

**Models** (`model/`)
Core domain entities:
- [User.java](src/main/java/shortly/mandmcorp/dev/shortly/model/User.java) - System users (implements UserDetails)
- [Parcel.java](src/main/java/shortly/mandmcorp/dev/shortly/model/Parcel.java) - Parcel information with compound indexes
- [DeliveryAssignments.java](src/main/java/shortly/mandmcorp/dev/shortly/model/DeliveryAssignments.java) - Delivery assignments to riders
- Office, Location, Shelf - Office management entities
- Reconcilations - Financial reconciliation records
- RiderStatusModel, RiderInfo - Rider information
- VerificationToken - For user verification
- UserAction - User activity tracking (currently not in use)

**DTOs** (`dto/request/` and `dto/response/`)
Request and response data transfer objects for API endpoints.

**Configuration** (`config/`)
- [SecurityConfig.java](src/main/java/shortly/mandmcorp/dev/shortly/config/security/SecurityConfig.java) - Spring Security with JWT filter, CORS
- [JWTConfig.java](src/main/java/shortly/mandmcorp/dev/shortly/config/security/JWTConfig.java) - JWT configuration
- [JWTAuthenticationFilter.java](src/main/java/shortly/mandmcorp/dev/shortly/config/security/JWTAuthenticationFilter.java) - JWT token validation filter
- MongoConfig, AsyncConfig, WebClientConfig, OpenApiConfig
- [RateLimitFilter.java](src/main/java/shortly/mandmcorp/dev/shortly/config/RateLimitFilter.java) - Request rate limiting with Bucket4j
- MNotifyConfig, FrontEndServerConfig

**Security** (`security/`)
- [UserDetailService.java](src/main/java/shortly/mandmcorp/dev/shortly/security/UserDetailService.java) - Custom UserDetailsService implementation

**Exceptions** (`exceptions/`)
Custom exceptions with [GlobalExceptionHandler.java](src/main/java/shortly/mandmcorp/dev/shortly/exceptions/GlobalExceptionHandler.java):
- UserNotFoundException, EntityNotFound, EntityAlreadyExist
- UnAthorizeException (note typo), WrongCredentialsException, InsufficientPrivilegesException
- ActionNotAllowed

**Utilities** (`utils/`)
- OtpUtil, NotificationUtil
- ParcelMapper, OfficeMapper
- WebRequestUtil

**Aspects** (`aspect/`)
- [UserActionAspect.java](src/main/java/shortly/mandmcorp/dev/shortly/aspect/UserActionAspect.java) - AOP for tracking user actions (currently commented out)

**Annotations** (`annotation/`)
- TrackUserAction - Custom annotation for user action tracking

**Enums** (`enums/`)
- UserRole (ADMIN, RIDER, FRONTDESK, MANAGER)
- UserStatusEnum, DeliveryStatus, RiderStatus, ParcelTypes, ContactType, ReconcilationType

## Authentication & Security

- Phone number is used as username for authentication
- JWT tokens are required for all endpoints except:
  - `/shortly/api-user/**` (public user endpoints)
  - `/shortly/health/**` (health checks)
  - `/shortly/swagger-ui/**` and `/shortly/v3/api-docs/**` (API documentation)
- Authentication uses BCrypt password encoding
- Stateless sessions (no server-side session storage)
- User roles determine access to endpoints (ADMIN, RIDER, FRONTDESK, MANAGER)

## Database

### MongoDB collections
- `users` - User accounts
- `parcelss` - Parcels (note: triple 's')
- `delivery_assignmentsss` - Delivery assignments (note: triple 's')
- `offices`, `locations`, `shelves` - Office infrastructure
- `reconcilations` - Financial reconciliation
- `rider_status` - Rider availability status
- `verification_tokens` - User verification
- `contacts` - Contact information

### Indexes
The Parcel and DeliveryAssignments models use compound indexes for query optimization:
- Parcel: office_delivered_idx, office_homedelivery_idx, office_called_idx, search_parcels_idx
- DeliveryAssignments: rider_status_idx, office_status_idx, office_payed_idx, rider_phone_status_idx, office_assigned_idx

## Testing

- Test configuration: [src/test/resources/application-test.yml](src/test/resources/application-test.yml)
- Main test: [ShortlyApplicationTests.java](src/test/java/shortly/mandmcorp/dev/shortly/ShortlyApplicationTests.java)

## Key Implementation Patterns

### Service Pattern
Services follow interface-implementation pattern under `service/{domain}/` and `service/{domain}/impl/`

### Error Handling
All exceptions are handled by GlobalExceptionHandler which returns structured ErrorResponse objects

### Data Auditing
Models use `@CreatedDate` and `@LastModifiedDate` annotations with `@EnableMongoAuditing`

### Async Operations
Configured via AsyncConfig for non-blocking operations

### Notification System
Template pattern for notifications: NotificationRequestTemplate with EmailNotification and SMSNotification implementations

## Development Notes

### Known Issues
- Collection names have unusual suffixes (parcelss, delivery_assignmentsss)
- Typo in application.yml: `JWT_REFERESH_KEY` should be `JWT_REFRESH_KEY`
- Typo in exception: `UnAthorizeException` should be `UnauthorizedException`
- User action tracking aspect is commented out but infrastructure remains

### Lombok Usage
The project heavily uses Lombok annotations:
- `@Data` for getters/setters/toString/equals/hashCode
- `@Builder` for builder pattern
- `@AllArgsConstructor`, `@RequiredArgsConstructor` for constructors

### API Documentation
Access Swagger UI at `/shortly/swagger-ui/index.html` when the application is running
