package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.AssignDeviceRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ShelfRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.LocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.dto.response.DashboardStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.RiderLocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.RiderTrackResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.service.dashboard.AdminDashboardService;
import shortly.mandmcorp.dev.shortly.service.dashboard.RevenueDashboardService;
import shortly.mandmcorp.dev.shortly.dto.response.RevenueDashboardResponse;
import shortly.mandmcorp.dev.shortly.service.dashboard.RiderPerformanceDashboardService;
import shortly.mandmcorp.dev.shortly.dto.response.RiderPerformanceDashboardResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.service.tracking.RiderTrackingService;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.impl.UserService;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.UserAction;
import shortly.mandmcorp.dev.shortly.model.ParcelSystemLog;
import shortly.mandmcorp.dev.shortly.dto.response.CallerStatsResponse;

@RestController
@AllArgsConstructor
@RequestMapping("/api-admin")
@Tag(name = "Admin Management", description = "APIs for admin operations")
public class AdminController {
    private final UserService userService;
    private final OfficeServiceInterface officeService;
    private final ParcelServiceInterface parcelService;
    private final RiderServiceInterface riderService;
    private final RiderTrackingService riderTrackingService;
    private final AdminDashboardService adminDashboardService;
    private final RevenueDashboardService revenueDashboardService;
    private final RiderPerformanceDashboardService riderPerformanceDashboardService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Admin endpoint to register a new user")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    @TrackUserAction(action = "REGISTER_USER", description = "Admin registered a new user")
    public UserRegistrationResponse registerUser(@RequestBody @Valid UserRegistrationRequest userRegistrationRequest) {
        return  userService.register(userRegistrationRequest);
    }
    
    @PostMapping("/office")
    @Operation(summary = "Add a new office", description = "Admin endpoint to add a new office")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Office added successfully"),
        @ApiResponse(responseCode = "409", description = "Office code already exists")
    })
    @TrackUserAction(action = "ADD_OFFICE", description = "Admin added a new office")
    public OfficeResponse addOffice(@RequestBody @Valid OfficeRequest officeRequest) {
        return officeService.addOffice(officeRequest);
    }

    @PostMapping("/shelf")
    @Operation(summary = "Add a new office", description = "Admin endpoint and manager endpoint to add new office shelf")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "shelf added successfully"),
        @ApiResponse(responseCode = "409", description = "shelf already exists")
    })
    @TrackUserAction(action = "ADD_SHELF", description = "Admin added a new shelf")
    public UserResponse addShelf(@RequestBody @Valid ShelfRequest shelf) {
        return officeService.addShelf(shelf);
    }

   
    
    @PostMapping("/location")
    @Operation(summary = "Add a new location", description = "Admin endpoint to add a new location")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location added successfully"),
        @ApiResponse(responseCode = "409", description = "Location name already exists")
    })
    @TrackUserAction(action = "ADD_LOCATION", description = "Admin added a new location")
    public LocationResponse addLocation(@RequestBody @Valid LocationRequest locationRequest) {
        return officeService.addLocation(locationRequest);
    }
    
    @PutMapping("/office/{id}")
    @Operation(summary = "Update an office", description = "Admin endpoint to update office details")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Office updated successfully"),
        @ApiResponse(responseCode = "404", description = "Office not found")
    })
    @TrackUserAction(action = "UPDATE_OFFICE", description = "Admin updated office details")
    public OfficeResponse updateOffice(@PathVariable String id, @RequestBody @Valid OfficeUpdateRequest updateRequest) {
        return officeService.updateOffice(id, updateRequest);
    }
    
    @PutMapping("/location/{id}")
    @Operation(summary = "Update a location", description = "Admin endpoint to update location details")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location updated successfully"),
        @ApiResponse(responseCode = "404", description = "Location not found")
    })
    @TrackUserAction(action = "UPDATE_LOCATION", description = "Admin updated location details")
    public LocationResponse updateLocation(@PathVariable String id, @RequestBody @Valid LocationUpdateRequest updateRequest) {
        return officeService.updateLocation(id, updateRequest);
    }

    @PutMapping("/user/{userId}/status/{status}")
    @Operation(summary = "Change user availability status", description = "Admin endpoint to change user availability status")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User status changed successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "400", description = "Invalid status value")
    })
    @TrackUserAction(action = "CHANGE_USER_STATUS", description = "Admin changed user availability status")
    public UserResponse chageUserAvailabiltyStatus(@PathVariable String userId, @PathVariable String status) {
        return userService.chageUserAvailabiltyStatus(userId, status);
    }


    @DeleteMapping("/user/{userId}")
    @Operation(summary = "Delete a user", description = "Admin endpoint to delete a user")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })

    @TrackUserAction(action = "DELETE_USER", description = "Admin deleted a user")
    public UserResponse deleteUser(@PathVariable String userId) {
        return userService.deleteUser(userId);
    }
    
    @GetMapping("/users")
    @Operation(summary = "Get users", description = "Admin/Manager endpoint to retrieve users with pagination. Provide officeId to filter by office, or omit to return all users.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    @TrackUserAction(action = "VIEW_ALL_USERS", description = "Admin/Manager viewed users")
    public Page<User> getAllUsers(
            @RequestParam(required = false) String officeId,
            Pageable pageable) {
        return userService.getUsers(officeId, pageable);
    }


    @GetMapping("/parcels")
    @Operation(summary = "Search parcels", description = "Search parcels with various filters and pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels retrieved successfully")
    })
    @TrackUserAction(action = "SEARCH_PARCELS", description = "Admin/Manager searched parcels")
    public Page<Parcel> searchParcels(
            @RequestParam(required = false) Boolean isPOD,
            @RequestParam(required = false) Boolean isDelivered,
            @RequestParam(required = false) Boolean isParcelAssigned,
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) Boolean hasCalled,
            Pageable pageable) {
        return parcelService.searchParcels(isPOD, isDelivered, isParcelAssigned, officeId, driverId, hasCalled, pageable, false);
    }

    @GetMapping("/reconciliations")
    @Operation(summary = "Get office reconciliations", description = "Get paginated reconciliations for all riders in the authenticated manager's office")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reconciliations retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "User is not a manager or admin")
    })
    @TrackUserAction(action = "VIEW_OFFICE_RECONCILIATIONS", description = "Manager/Admin viewed office reconciliations")
    public Page<Reconcilations> getOfficeReconciliations(Pageable pageable) {
        return riderService.getOfficeReconciliations(pageable);
    }


    @GetMapping("/user-actions")
    @Operation(summary = "Get user actions", description = "Get paginated user actions sorted by date descending. Optionally filter by user email.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User actions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "VIEW_USER_ACTIONS", description = "Admin/Manager viewed user action logs")
    public Page<UserAction> getUserActions(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String phoneNumber,
            Pageable pageable) {
        return userService.getUserActions(userEmail, officeId, phoneNumber, pageable);
    }

    @GetMapping("/caller-stats")
    @Operation(summary = "Get caller stats", description = "Returns call statistics for a caller by phone number. Use period=all for all-time or period=month for the current month.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stats retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "User is not an admin or manager")
    })
    @TrackUserAction(action = "VIEW_CALLER_STATS", description = "Admin/Manager viewed caller statistics")
    public CallerStatsResponse getCallerStats(
            @RequestParam String callerPhoneNumber,
            @RequestParam(defaultValue = "all") String period) {
        return parcelService.getCallerStats(callerPhoneNumber, period);
    }

    @GetMapping("/parcel-system-logs")
    @Operation(summary = "Get parcel system logs", description = "Returns paginated ParcelSystemLog entries. Filter by officeId and/or parcelId. If no officeId is given, returns logs from all offices.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logs retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "User is not an admin or manager")
    })
    @TrackUserAction(action = "VIEW_PARCEL_SYSTEM_LOGS", description = "Admin/Manager viewed parcel system logs")
    public Page<ParcelSystemLog> getParcelSystemLogs(
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String parcelId,
            Pageable pageable) {
        return parcelService.getParcelSystemLogs(officeId, parcelId, pageable);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics", description = "Returns overview cards, revenue breakdown, daily trends, parcel pipeline, station snapshot, and top riders. Date params are epoch milliseconds. Defaults to the last 30 days when omitted.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "VIEW_DASHBOARD_STATS", description = "Admin/Manager viewed dashboard statistics")
    public DashboardStatsResponse getDashboardStats(
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return adminDashboardService.getStats(officeId, startDate, endDate);
    }

    @GetMapping("/rider-performance-dashboard")
    @Operation(summary = "Get rider performance dashboard", description = "Returns daily and monthly station earnings trend, and a rider leaderboard with deliveries, failed, revenue, outstanding, rating and avg time. Date params are epoch milliseconds. Defaults to the last 30 days when omitted.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rider performance statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "VIEW_RIDER_PERFORMANCE_DASHBOARD", description = "Admin/Manager viewed rider performance dashboard")
    public RiderPerformanceDashboardResponse getRiderPerformanceDashboard(
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return riderPerformanceDashboardService.getStats(officeId, startDate, endDate);
    }

    @GetMapping("/revenue-dashboard")
    @Operation(summary = "Get revenue analytics dashboard", description = "Returns KPI cards, daily revenue/collected/outstanding trend, revenue by station, payment method trend, revenue by type (POD vs Non-POD), payment method totals, and revenue by day of week. Date params are epoch milliseconds. Defaults to the last 30 days when omitted.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Revenue statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "VIEW_REVENUE_DASHBOARD", description = "Admin/Manager viewed revenue analytics dashboard")
    public RevenueDashboardResponse getRevenueDashboard(
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return revenueDashboardService.getRevenueStats(officeId, startDate, endDate);
    }

    @GetMapping("/riders/location")
    @Operation(summary = "Get rider current location", description = "Returns the current GPS location of a rider identified by phone number")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Rider not found"),
        @ApiResponse(responseCode = "422", description = "Rider has no GPS device assigned"),
        @ApiResponse(responseCode = "502", description = "Tracking API error")
    })
    @TrackUserAction(action = "VIEW_RIDER_LOCATION", description = "Admin/Manager viewed rider GPS location")
    public RiderLocationResponse getRiderLocation(@RequestParam String phoneNumber) {
        return riderTrackingService.getRiderLocationByPhone(phoneNumber);
    }

    @GetMapping("/riders/track")
    @Operation(summary = "Get rider track history", description = "Returns GPS track points for a rider between a start and end time. Times must be in UTC format: yyyy-MM-dd HH:mm:ss. Max range is 2 days within the last 3 months.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Track history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Rider not found"),
        @ApiResponse(responseCode = "422", description = "Rider has no GPS device assigned"),
        @ApiResponse(responseCode = "502", description = "Tracking API error")
    })
    @TrackUserAction(action = "VIEW_RIDER_TRACK", description = "Admin/Manager viewed rider GPS track history")
    public RiderTrackResponse getRiderTrack(
            @RequestParam String phoneNumber,
            @RequestParam String beginTime,
            @RequestParam String endTime) {
        return riderTrackingService.getRiderTrack(phoneNumber, beginTime, endTime);
    }

    @PutMapping("/riders/device")
    @Operation(summary = "Assign GPS device to rider", description = "Assigns or updates the GPS device IMEI for a rider identified by phone number")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Device assigned successfully"),
        @ApiResponse(responseCode = "404", description = "Rider not found"),
        @ApiResponse(responseCode = "400", description = "User is not a rider")
    })
    @TrackUserAction(action = "ASSIGN_RIDER_DEVICE", description = "Admin/Manager assigned GPS device to rider")
    public String assignRiderDevice(@RequestParam String phoneNumber, @RequestBody @Valid AssignDeviceRequest request) {
        return riderTrackingService.assignDeviceImei(phoneNumber, request.getDeviceImei());
    }

    @GetMapping("/reconciliations/by-date")
    @Operation(summary = "Get reconciliations by date", description = "Get paginated reconciliations for a specific date filtered by createdAt or reconciledAt. Requires MANAGER or ADMIN role.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reconciliations retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "User is not a manager or admin")
    })
    @TrackUserAction(action = "VIEW_RECONCILIATIONS_BY_DATE", description = "Manager/Admin viewed reconciliations by date")
    public Page<DeliveryAssignments> getReconciliationsByDate(
            @RequestParam Long date,
            @RequestParam String officeId,
            @RequestParam(defaultValue = "false") boolean useReconciledAt,
            Pageable pageable) {
        return riderService.getReconciliationsByDate(date, officeId, useReconciledAt, pageable);
    }
}
