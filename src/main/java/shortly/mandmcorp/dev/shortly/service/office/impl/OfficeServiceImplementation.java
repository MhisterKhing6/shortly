package shortly.mandmcorp.dev.shortly.service.office.impl;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.LocationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.LocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.LocationWithOfficesResponse;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.exceptions.EntityAlreadyExist;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.model.Location;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.LocationRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.OfficeMapper;

@Service
@Slf4j
@AllArgsConstructor
public class OfficeServiceImplementation implements OfficeServiceInterface {
    
    private final OfficeRepository officeRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final OfficeMapper officeMapper;
    
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public OfficeResponse addOffice(OfficeRequest officeRequest) {
        log.info("Adding new office: {}", officeRequest.getName());
        log.info("Looking for location with ID: {}", officeRequest.getLocationId());
        Location location = locationRepository.findById(officeRequest.getLocationId())
            .orElseThrow(() -> {
                log.error("Location not found with ID: {}", officeRequest.getLocationId());
                return new EntityNotFound("Location not found with ID: " + officeRequest.getLocationId());
            });
        
        officeRepository.findFirstByNameAndLocation(officeRequest.getName(), location)
            .ifPresent(office -> {
                throw new EntityAlreadyExist("Office with this name already exists in this location");
            });
        
        Office office = officeMapper.toEntity(officeRequest);
        office.setCode(generateOfficeCode());
        office.setLocation(location);
        
        if(officeRequest.getManagerId() != null) {
            User manager = userRepository.findById(officeRequest.getManagerId()).orElse(null);
            office.setManager(manager);
        }

        Office savedOffice = officeRepository.save(office);
        log.info("Office saved successfully with ID: {} and code: {}", savedOffice.getId(), savedOffice.getCode());
        
        return officeMapper.toResponse(savedOffice);
    }
    
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public LocationResponse addLocation(LocationRequest locationRequest) {
        log.info("Adding new location: {}", locationRequest.getName());
        
        try {
            Location existingLocation = locationRepository.findByName(locationRequest.getName());
            if(existingLocation != null) {
                throw new EntityAlreadyExist("Location with name already exists");
            }
            
            Location location = new Location();
            location.setName(locationRequest.getName());
            location.setRegion(locationRequest.getRegion());
            location.setCountry(locationRequest.getCountry());
            
            Location savedLocation = locationRepository.save(location);
            log.info("Location saved successfully with ID: {}", savedLocation.getId());
            
            LocationResponse response = new LocationResponse();
            response.setId(savedLocation.getId());
            response.setName(savedLocation.getName());
            response.setRegion(savedLocation.getRegion());
            response.setCountry(savedLocation.getCountry());
            
            return response;
        } catch (Exception e) {
            log.error("Error saving location: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public OfficeResponse updateOffice(String officeId, OfficeUpdateRequest updateRequest) {
        log.info("Updating office with ID: {}", officeId);
        
        Office office = officeRepository.findById(officeId)
            .orElseThrow(() -> new EntityNotFound("Office not found"));
        
        if(updateRequest.getName() != null) office.setName(updateRequest.getName());
        if(updateRequest.getAddress() != null) office.setAddress(updateRequest.getAddress());
        
        if(updateRequest.getLocationId() != null) {
            Location location = locationRepository.findById(updateRequest.getLocationId())
                .orElseThrow(() -> new EntityNotFound("Location not found"));
            office.setLocation(location);
        }
        
        if(updateRequest.getManagerId() != null) {
            User manager = userRepository.findById(updateRequest.getManagerId())
                .orElseThrow(() -> new EntityNotFound("Manager not found"));
            office.setManager(manager);
        }
        
        Office updatedOffice = officeRepository.save(office);
        log.info("Office updated successfully with ID: {}", updatedOffice.getId());
        
        return officeMapper.toResponse(updatedOffice);
    }
    
    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public LocationResponse updateLocation(String locationId, LocationUpdateRequest updateRequest) {
        log.info("Updating location with ID: {}", locationId);
        
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new EntityNotFound("Location not found"));
        
        if(updateRequest.getName() != null) location.setName(updateRequest.getName());
        if(updateRequest.getRegion() != null) location.setRegion(updateRequest.getRegion());
        if(updateRequest.getCountry() != null) location.setCountry(updateRequest.getCountry());
        
        Location updatedLocation = locationRepository.save(location);
        log.info("Location updated successfully with ID: {}", updatedLocation.getId());
        
        LocationResponse response = new LocationResponse();
        response.setId(updatedLocation.getId());
        response.setName(updatedLocation.getName());
        response.setRegion(updatedLocation.getRegion());
        response.setCountry(updatedLocation.getCountry());
        
        return response;
    }
    
    @Override
    public List<LocationWithOfficesResponse> getAllLocationsWithOffices(String locationName, String officeName) {
        log.info("Getting all locations with offices. Filters - locationName: {}, officeName: {}", locationName, officeName);
        
        List<Location> locations = (locationName != null && !locationName.isEmpty()) 
            ? locationRepository.findByNameContainingIgnoreCase(locationName)
            : locationRepository.findAll();
        
        return locations.stream().map(location -> {
            List<Office> offices = officeRepository.findByLocation(location);
            
            if(officeName != null && !officeName.isEmpty()) {
                offices = offices.stream()
                    .filter(office -> office.getName().toLowerCase().contains(officeName.toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            LocationWithOfficesResponse response = new LocationWithOfficesResponse();
            response.setId(location.getId());
            response.setName(location.getName());
            response.setRegion(location.getRegion());
            response.setCountry(location.getCountry());
            response.setOffices(offices.stream().map(officeMapper::toResponse).collect(Collectors.toList()));
            
            return response;
        }).collect(Collectors.toList());
    }
    
    @Override
    public LocationWithOfficesResponse getLocationById(String locationId) {
        log.info("Getting location by ID: {}", locationId);
        
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new EntityNotFound("Location not found"));
        
        List<Office> offices = officeRepository.findByLocation(location);
        
        LocationWithOfficesResponse response = new LocationWithOfficesResponse();
        response.setId(location.getId());
        response.setName(location.getName());
        response.setRegion(location.getRegion());
        response.setCountry(location.getCountry());
        response.setOffices(offices.stream().map(officeMapper::toResponse).collect(Collectors.toList()));

        return response;
    }
    
    private String generateOfficeCode() {
        Random random = new Random();
        String code;
        do {
            code = "OFF" + String.format("%06d", random.nextInt(1000000));
        } while (officeRepository.findByCode(code).isPresent());
        return code;
    }
}