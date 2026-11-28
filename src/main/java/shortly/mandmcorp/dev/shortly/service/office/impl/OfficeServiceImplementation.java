package shortly.mandmcorp.dev.shortly.service.office.impl;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.AddAddressRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationRequest;
import shortly.mandmcorp.dev.shortly.dto.request.LocationUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeRequest;
import shortly.mandmcorp.dev.shortly.dto.request.OfficeUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ShelfRequest;
import shortly.mandmcorp.dev.shortly.dto.response.LocationResponse;
import shortly.mandmcorp.dev.shortly.dto.response.LocationWithOfficesResponse;
import shortly.mandmcorp.dev.shortly.dto.response.OfficeResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.exceptions.EntityAlreadyExist;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Address;
import shortly.mandmcorp.dev.shortly.model.Location;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Shelf;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.AddressRepository;
import shortly.mandmcorp.dev.shortly.repository.LocationRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.ShelfRepository;
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
    private final ShelfRepository shelfRepository;
    private final AddressRepository addressRepository;
    private final ParcelRepository parcelRepository;
    private final shortly.mandmcorp.dev.shortly.repository.DeliveryAssignmentsRepository deliveryAssignmentsRepository;
    private final shortly.mandmcorp.dev.shortly.utils.DeliveryLocationValidator locationValidator;
    
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
        
        locationValidator.validateStation(officeRequest.getLatitude(), officeRequest.getLongitude());
        validatePickupCharge(officeRequest.getPickupChargeEnabled(), officeRequest.getPickupChargeAmount());
        Office office = officeMapper.toEntity(officeRequest);
        if (Boolean.FALSE.equals(office.getPickupChargeEnabled())) office.setPickupChargeAmount(null);
        office.setCode(generateOfficeCode());
        office.setLocation(location);

        // Scope the office to the logged-in user's company.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedInUser = (User) auth.getPrincipal();
        office.setCompanyId(loggedInUser.getCompanyId());

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
            location.setCompanyId(loggedInCompanyId());

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
    
    /** Returns the logged-in user's companyId, or null. */
    private String loggedInCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof User user) ? user.getCompanyId() : null;
    }

    /** Rejects (as not-found, to avoid leaking cross-tenant existence) if the entity isn't in the caller's company. */
    private void assertOwnedByCompany(String entityCompanyId, String notFoundMessage) {
        if (!java.util.Objects.equals(entityCompanyId, loggedInCompanyId())) {
            throw new EntityNotFound(notFoundMessage);
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public OfficeResponse updateOffice(String officeId, OfficeUpdateRequest updateRequest) {
        log.info("Updating office with ID: {}", officeId);

        Office office = officeRepository.findById(officeId)
            .orElseThrow(() -> new EntityNotFound("Office not found"));
        assertOwnedByCompany(office.getCompanyId(), "Office not found");

        if(updateRequest.getName() != null) office.setName(updateRequest.getName());
        if(updateRequest.getAddress() != null) office.setAddress(updateRequest.getAddress());
        if(updateRequest.getPhoneNumber() != null) office.setPhoneNumber(updateRequest.getPhoneNumber());

        if(updateRequest.getCode() != null && !updateRequest.getCode().isBlank()
                && !updateRequest.getCode().trim().equals(office.getCode())) {
            String code = updateRequest.getCode().trim();
            officeRepository.findByCode(code).ifPresent(other -> {
                throw new EntityAlreadyExist("Office code already in use");
            });
            office.setCode(code);
        }

        if(updateRequest.getLatitude() != null || updateRequest.getLongitude() != null) {
            locationValidator.validateStation(updateRequest.getLatitude(), updateRequest.getLongitude());
            office.setLatitude(updateRequest.getLatitude());
            office.setLongitude(updateRequest.getLongitude());
        }

        if(updateRequest.getPickupChargeEnabled() != null) {
            applyPickupCharge(office, updateRequest.getPickupChargeEnabled(), updateRequest.getPickupChargeAmount());
        }
        if(updateRequest.getOpeningHours() != null) office.setOpeningHours(updateRequest.getOpeningHours());
        if(updateRequest.getDriverPayPolicy() != null) {
            office.setDriverPayPolicy(updateRequest.getDriverPayPolicy());
        }
        
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
        assertOwnedByCompany(location.getCompanyId(), "Location not found");

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
        
        // Only the caller's company: its locations and stations (vendors included).
        User caller = shortly.mandmcorp.dev.shortly.utils.CurrentUser.getOrNull();
        String company = caller != null ? caller.getCompanyId() : null;
        return locations.stream().map(location -> {
            List<Office> offices = officeRepository.findByLocation(location).stream()
                .filter(o -> caller == null || java.util.Objects.equals(o.getCompanyId(), company))
                .collect(Collectors.toList());
            if (caller != null && offices.isEmpty() && !java.util.Objects.equals(location.getCompanyId(), company)) {
                return null;
            }
            
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
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
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

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public UserResponse addShelf(ShelfRequest shelf ) {
        Office office = officeRepository.findById(shelf.getOfficeId())
            .orElseThrow(() -> new EntityNotFound("Office not found"));
        assertOwnedByCompany(office.getCompanyId(), "Office not found");
        Shelf savedShelf = shelfRepository.findByNameAndOffice(shelf.getName(), office);
        if(savedShelf != null) {
            throw new EntityAlreadyExist("Shelf with the same name already exist");
        }
        Shelf shelfEntity = new Shelf();
        shelfEntity.setName(shelf.getName());
        shelfEntity.setOffice(office);
        shelfRepository.save(shelfEntity);
        return new UserResponse("shelf saved successfully", shelfEntity.getId() );
    }

    @Override
    public List<Shelf> getOfficeShelf(String officeId) {
        Office office = officeRepository.findById(officeId)
            .orElseThrow(() -> new EntityNotFound("Office not found"));
        return shelfRepository.findByOffice(office);
    }
    @Override
    public Address addAddres(AddAddressRequest request){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User frontDesk = (User) auth.getPrincipal();
        Address newAddress = new Address();
        newAddress.setName(request.getName());
        newAddress.setCost(request.getCost());
        locationValidator.validateDropoff(request.getLatitude(), request.getLongitude());
        newAddress.setLatitude(request.getLatitude());
        newAddress.setLongitude(request.getLongitude());
        newAddress.setLocationName(request.getLocationName());
        newAddress.setOfficeId(requireOffice(frontDesk));
        addressRepository.save(newAddress);
        return newAddress;
    }
    @Override
    public List<Address> getAllAddressesByName(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        User frontDesk = (User) auth.getPrincipal();
        String officeId = shortly.mandmcorp.dev.shortly.utils.CurrentUser.officeId(frontDesk);
        if (officeId == null) return List.of();
         if (name == null || name.isBlank()) {
            return addressRepository.findAllByOfficeId(officeId);
            }
            return addressRepository.findAllByOfficeIdAndNameContainingIgnoreCase(officeId, name);
    }
    @Override
    public List<Parcel> getParcelsByAddressNumber(String phoneNumber) {
        return parcelRepository.findByRecieverPhoneNumberAndIsDeliveredFalse(phoneNumber);
    }

    @Override
    @PreAuthorize("hasRole('MANAGER')")
    public UserResponse updateOwnPickupCharge(Boolean enabled, Double amount,
            shortly.mandmcorp.dev.shortly.enums.DriverPayPolicy driverPayPolicy) {
        User manager = shortly.mandmcorp.dev.shortly.utils.CurrentUser.get();
        String officeId = shortly.mandmcorp.dev.shortly.utils.CurrentUser.officeId(manager);
        if (officeId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Manager has no assigned office");
        }
        if (enabled == null && driverPayPolicy == null) {
            throw badRequest("pickupChargeEnabled is required");
        }
        Office office = officeRepository.findById(officeId)
            .orElseThrow(() -> new EntityNotFound("Office not found"));
        if (enabled != null) applyPickupCharge(office, enabled, amount);
        if (driverPayPolicy != null) office.setDriverPayPolicy(driverPayPolicy);
        officeRepository.save(office);
        return new UserResponse("Pickup charge saved", officeId);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deleteOffice(String officeId) {
        Office office = officeRepository.findById(officeId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Office not found"));
        if (parcelRepository.existsByOfficeId(officeId)) {
            throw conflict("Station has parcels; it can't be deleted");
        }
        if (userRepository.existsByOfficeIdsContaining(officeId)) {
            throw conflict("Station has users; move or remove them first");
        }
        if (deliveryAssignmentsRepository.existsByOfficeIdAndStatusNotIn(officeId, List.of(
                shortly.mandmcorp.dev.shortly.enums.DeliveryStatus.DELIVERED,
                shortly.mandmcorp.dev.shortly.enums.DeliveryStatus.RETURNED,
                shortly.mandmcorp.dev.shortly.enums.DeliveryStatus.COMPLETED,
                shortly.mandmcorp.dev.shortly.enums.DeliveryStatus.CANCELLED))) {
            throw conflict("Station has open delivery assignments");
        }
        officeRepository.delete(office);
        return new UserResponse("Office deleted", officeId);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deleteLocation(String locationId) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Location not found"));
        if (!officeRepository.findByLocation(location).isEmpty()) {
            throw conflict("Location has stations; delete or move them first");
        }
        locationRepository.delete(location);
        return new UserResponse("Location deleted", locationId);
    }

    private static String requireOffice(User user) {
        String officeId = shortly.mandmcorp.dev.shortly.utils.CurrentUser.officeId(user);
        if (officeId == null) throw badRequest("Add a station to your account first");
        return officeId;
    }

    private void applyPickupCharge(Office office, boolean enabled, Double amount) {
        validatePickupCharge(enabled, amount);
        office.setPickupChargeEnabled(enabled);
        office.setPickupChargeAmount(enabled ? amount : null);
    }

    private void validatePickupCharge(Boolean enabled, Double amount) {
        if (Boolean.TRUE.equals(enabled) && (amount == null || amount <= 0)) {
            throw badRequest("pickupChargeAmount must be greater than 0 when the pickup charge is enabled");
        }
    }

    private static org.springframework.web.server.ResponseStatusException badRequest(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    private static org.springframework.web.server.ResponseStatusException conflict(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, message);
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