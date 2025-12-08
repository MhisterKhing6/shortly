package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.response.LocationWithOfficesResponse;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;

@RestController
@AllArgsConstructor
@RequestMapping("/api/offices")
@Tag(name = "Office Management", description = "APIs for office and location operations")
public class OfficeController {
    private final OfficeServiceInterface officeService;
    
    @GetMapping("/locations")
    @Operation(summary = "Get all locations with offices", description = "Retrieve all locations with their associated offices. Optional filters for location name and office name")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Locations retrieved successfully")
    })
    public List<LocationWithOfficesResponse> getAllLocationsWithOffices(
            @RequestParam(required = false) String locationName,
            @RequestParam(required = false) String officeName) {
        return officeService.getAllLocationsWithOffices(locationName, officeName);
    }
    
    @GetMapping("/locations/{id}")
    @Operation(summary = "Get location by ID", description = "Retrieve a specific location with its offices by location ID")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Location not found")
    })
    public LocationWithOfficesResponse getLocationById(@PathVariable String id) {
        return officeService.getLocationById(id);
    }
}
