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
import shortly.mandmcorp.dev.shortly.dto.request.LocationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ShelfRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.LocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.impl.UserService;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;

@RestController
@AllArgsConstructor
@RequestMapping("/api-admin")
@Tag(name = "Admin Management", description = "APIs for admin operations")
public class AdminController {
    private final UserService userService;
    private final OfficeServiceInterface officeService;
    private final ParcelServiceInterface parcelService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Admin endpoint to register a new user")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User registered successfully"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
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
    public UserResponse deleteUser(@PathVariable String userId) {
        return userService.deleteUser(userId);
    }
    
    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Admin/Manager endpoint to retrieve all users with pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    public Page<User> getAllUsers(Pageable pageable) {
        return userService.getAllUsers(pageable);
    }


    @GetMapping("/parcels")
    @Operation(summary = "Search parcels", description = "Search parcels with various filters and pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels retrieved successfully")
    })
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
}
