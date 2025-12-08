package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import shortly.mandmcorp.dev.shortly.dto.request.LocationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.LocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.impl.UserService;

@RestController
@AllArgsConstructor
@RequestMapping("/api-admin")
@Tag(name = "Admin Management", description = "APIs for admin operations")
public class AdminController {
    private final UserService userService;
    private final OfficeServiceInterface officeService;  
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
    public OfficeResponse addOffice(@RequestBody @Valid OfficeRequest officeRequest) {
        return officeService.addOffice(officeRequest);
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
}
