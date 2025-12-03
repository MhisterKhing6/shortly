package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;



@RestController
@RequestMapping("/api-frontdesk")
@AllArgsConstructor
@Tag(name = "Front Desk Management", description = "APIs for front desk operations")
public class FrontDeskController {
    
    private final ParcelServiceInterface parcelService;
    @PostMapping("/parcel")
    @Operation(summary = "Add a new parcel", description = "Create a new parcel entry in the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parcel data")
    })
    public ParcelResponse addParcel(@RequestBody @Valid ParcelRequest parcelRequest) {
        return parcelService.addParcel(parcelRequest);
    }

    @PutMapping("/parcel/{id}")
    public String updateParcel(@PathVariable String id, @RequestBody String entity) {
        return null;
    }

    @GetMapping("/parcel/{id}")
    public String getParcel(@PathVariable String id) {
        return null;
    }


}
