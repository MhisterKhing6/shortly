package shortly.mandmcorp.dev.shortly.service.parcel.impl;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.ParcelMapper;

@Service
@Slf4j
@AllArgsConstructor
public class ParcelServiceImplementation implements ParcelServiceInterface {
    
    private final ParcelRepository parcelRepository;
    private final ParcelMapper parcelMapper;
    
    @Override
    public ParcelResponse addParcel(ParcelRequest parcelRequest) {
        log.info("Adding new parcel for sender: {}", parcelRequest.getSenderName());
        
        Parcel parcel = parcelMapper.toEntity(parcelRequest);
        log.debug("Mapped ParcelRequest to Parcel entity");
        
        Parcel savedParcel = parcelRepository.save(parcel);
        log.info("Parcel saved successfully with ID: {}", savedParcel.getParcelId());
        
        return parcelMapper.toResponse(savedParcel);
    }
}
