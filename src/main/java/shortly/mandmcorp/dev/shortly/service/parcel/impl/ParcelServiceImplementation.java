package shortly.mandmcorp.dev.shortly.service.parcel.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.CallCenterUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelReceivedRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.PickedUpRequest;
import shortly.mandmcorp.dev.shortly.dto.request.VendorParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CallCenterStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.CallerStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.dto.response.VendorDashboardResponse;
import shortly.mandmcorp.dev.shortly.dto.response.VendorParcelItem;
import shortly.mandmcorp.dev.shortly.dto.response.VendorStationGroup;
import shortly.mandmcorp.dev.shortly.enums.CallCenterCallOutCome;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.CalledParcelInfo;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.OfficeInfo;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.ParcelSystemLog;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;
import shortly.mandmcorp.dev.shortly.model.Shelf;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.CalledParcelInfoRepository;
import shortly.mandmcorp.dev.shortly.repository.DriverAssignmentRepository;
import shortly.mandmcorp.dev.shortly.repository.DriverReconcilationRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelSystemLogRepository;
import shortly.mandmcorp.dev.shortly.repository.ShelfRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.service.storage.R2Service;
import shortly.mandmcorp.dev.shortly.utils.NotificationUtil;
import shortly.mandmcorp.dev.shortly.utils.ParcelMapper;

@Service
@Slf4j
@AllArgsConstructor
public class ParcelServiceImplementation implements ParcelServiceInterface {

    private final ParcelRepository parcelRepository;
    private final ParcelMapper parcelMapper;
    private final OfficeRepository officeRepository;
    private final UserRepository userRepository;
    private final ShelfRepository shelfRepository;
    private final MongoTemplate mongoTemplate;
    private final DriverReconcilationRepository driverReconcilationRepository;
    private final CalledParcelInfoRepository calledParcelInfoRepository;
    private final ParcelSystemLogRepository parcelSystemLogRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    @Qualifier("smsNotification")
    private final NotificationInterface notification;
    private final R2Service r2Service;
    private final shortly.mandmcorp.dev.shortly.utils.BarcodeGenerator barcodeGenerator;
    private final shortly.mandmcorp.dev.shortly.utils.DeliveryLocationValidator locationValidator;
    private final shortly.mandmcorp.dev.shortly.service.counter.CounterService counterService;
    private final shortly.mandmcorp.dev.shortly.service.driverpay.DriverFeeLineService driverFeeLineService;
    private final shortly.mandmcorp.dev.shortly.service.common.IdempotencyService idempotencyService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final shortly.mandmcorp.dev.shortly.service.pricing.DeliveryPricingService deliveryPricingService;
    private final shortly.mandmcorp.dev.shortly.service.customer.CustomerPortalService customerPortalService;
    private final org.springframework.core.env.Environment environment;

    /** Receiver "your parcel has arrived" SMS with the /receive link; off unless RECEIVER_ARRIVAL_SMS=true. */
    private boolean arrivalSmsEnabled() {
        return Boolean.parseBoolean(environment.getProperty("shortly.sms.receiver-arrival-enabled", "false"));
    }

    /**
     * Resolves the barcode for a new parcel: honors a non-blank user-supplied value
     * (rejecting it with a 400 if it already exists); otherwise generates a unique one.
     * The generator is sequence-backed, so the uniqueness loop is just a safety net.
     */
    private String resolveBarCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim();
            if (parcelRepository.existsByBarCode(trimmed)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Barcode '" + trimmed + "' already exists. Please use a different barcode or leave it blank to auto-generate one.");
            }
            return trimmed;
        }
        String code;
        do {
            code = barcodeGenerator.generate();
        } while (parcelRepository.existsByBarCode(code));
        return code;
    }

    /**
     * Returns the logged-in user's companyId, or null if not available.
     * Every parcel query is scoped to this so companies never see each other's parcels.
     */
    private String loggedInCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getCompanyId();
        }
        return null;
    }

    /** Criteria that restricts a parcel query to the logged-in user's company. */
    private Criteria companyScope() {
        return Criteria.where("companyId").is(loggedInCompanyId());
    }

    /** Guards a single-parcel operation so a user can only act on their own company's parcels. */
    private void assertSameCompany(Parcel parcel) {
        if (!java.util.Objects.equals(parcel.getCompanyId(), loggedInCompanyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parcel does not belong to your company");
        }
    }

    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDashboardResponse getVendorDashboard(String search, shortly.mandmcorp.dev.shortly.enums.ParcelStatus status) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User vendor = (User) auth.getPrincipal();
        String vendorPhone = vendor.getPhoneNumber();

        // Status summary — always over ALL vendor parcels, unaffected by search/filter
        Query allQuery = new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("vendorId").is(vendorPhone)));
        List<Parcel> allParcels = mongoTemplate.find(allQuery, Parcel.class);
        java.util.Map<String, Long> statusSummary = new java.util.LinkedHashMap<>();
        for (shortly.mandmcorp.dev.shortly.enums.ParcelStatus s : shortly.mandmcorp.dev.shortly.enums.ParcelStatus.values()) {
            statusSummary.put(s.name(), 0L);
        }
        allParcels.forEach(p -> {
            if (p.getParcelStatus() != null) {
                statusSummary.merge(p.getParcelStatus().name(), 1L, Long::sum);
            }
        });

        // Filtered query for the grouped list
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(Criteria.where("vendorId").is(vendorPhone));

        if (status != null) {
            criteria.add(Criteria.where("parcelStatus").is(status));
        }

        if (search != null && !search.isBlank()) {
            criteria.add(new Criteria().orOperator(
                Criteria.where("_id").is(search),
                Criteria.where("receiverName").regex(search, "i"),
                Criteria.where("recieverPhoneNumber").regex(search, "i")
            ));
        }

        Query filteredQuery = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        filteredQuery.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        List<Parcel> filtered = mongoTemplate.find(filteredQuery, Parcel.class);

        // Group by destination office name
        java.util.Map<String, List<Parcel>> grouped = new java.util.LinkedHashMap<>();
        for (Parcel p : filtered) {
            String key = (p.getTo() != null && p.getTo().getOfficeName() != null)
                ? p.getTo().getOfficeName() : "Unknown";
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<VendorStationGroup> stations = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Parcel>> entry : grouped.entrySet()) {
            List<VendorParcelItem> items = new ArrayList<>();
            double totalAmount = 0;
            int podCount = 0;
            for (Parcel p : entry.getValue()) {
                items.add(VendorParcelItem.builder()
                    .parcelId(p.getParcelId())
                    .receiverName(p.getReceiverName())
                    .recieverPhoneNumber(p.getRecieverPhoneNumber())
                    .parcelDescription(p.getParcelDescription())
                    .deliveryFee(p.getVendorDeliveryFee())
                    .itemCost(p.getItemCost())
                    .parcelStatus(p.getParcelStatus())
                    .isPOD(p.isPOD())
                    .createdAt(p.getCreatedAt())
                    .build());
                totalAmount += p.getVendorDeliveryFee();
                if (p.isPOD()) podCount++;
            }
            String officeId = entry.getValue().get(0).getTo() != null
                ? entry.getValue().get(0).getTo().getOfficeId() : null;
            stations.add(VendorStationGroup.builder()
                .officeId(officeId)
                .officeName(entry.getKey())
                .parcelCount(entry.getValue().size())
                .podCount(podCount)
                .totalAmount(totalAmount)
                .parcels(items)
                .build());
        }

        return VendorDashboardResponse.builder()
            .statusSummary(statusSummary)
            .totalParcels(filtered.size())
            .totalStations(stations.size())
            .stations(stations)
            .build();
    }

    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public Parcel addVendorParcel(VendorParcelRequest request) {
        return addVendorParcel(request, null);
    }

    /** One partner parcel. A repeat with the same Idempotency-Key (24 h) returns the first parcel. */
    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public Parcel addVendorParcel(VendorParcelRequest request, String idempotencyKey) {
        return idempotencyService.run("vendor-parcel", idempotencyKey,
                () -> {
                    User vendor = shortly.mandmcorp.dev.shortly.utils.CurrentUser.get();
                    Parcel parcel = prepareVendorParcel(request, vendor, null);
                    return saveVendorParcel(parcel, request);
                },
                p -> List.of(p.getParcelId()),
                rec -> parcelRepository.findById(rec.getResultIds().get(0)).orElseThrow(() -> new EntityNotFound("Parcel not found")));
    }

    /**
     * A batch of partner parcels, all or nothing: every parcel is validated first, and if any is
     * invalid nothing is saved and the 400 names it ("Parcel 2 (Kofi Owusu): ...").
     */
    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public List<Parcel> addVendorParcels(List<VendorParcelRequest> requests, String idempotencyKey) {
        if (requests == null || requests.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parcels is required");
        if (requests.size() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 500 parcels per batch");
        return idempotencyService.run("vendor-batch", idempotencyKey,
                () -> {
                    User vendor = shortly.mandmcorp.dev.shortly.utils.CurrentUser.get();
                    List<Parcel> prepared = new ArrayList<>();
                    java.util.Set<String> batchCodes = new java.util.HashSet<>();
                    for (int i = 0; i < requests.size(); i++) {
                        VendorParcelRequest r = requests.get(i);
                        String label = "Parcel " + (i + 1) + (r != null && r.getReceiverName() != null ? " (" + r.getReceiverName() + ")" : "");
                        if (r == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + ": empty");
                        if (r.getBarCode() != null && !r.getBarCode().isBlank() && !batchCodes.add(r.getBarCode().trim())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + ": barcode is used twice in this batch");
                        }
                        prepared.add(prepareVendorParcel(r, vendor, label));
                    }
                    List<Parcel> saved = new ArrayList<>();
                    try {
                        transactionTemplate.executeWithoutResult(tx -> {
                            for (int i = 0; i < prepared.size(); i++) saved.add(saveVendorParcel(prepared.get(i), requests.get(i)));
                        });
                    } catch (org.springframework.dao.DuplicateKeyException e) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "A barcode in this batch was just used by another parcel; nothing was saved");
                    }
                    return saved;
                },
                list -> list.stream().map(Parcel::getParcelId).toList(),
                rec -> rec.getResultIds().stream()
                        .map(id -> parcelRepository.findById(id).orElseThrow(() -> new EntityNotFound("Parcel not found"))).toList());
    }

    /** Edit a partner parcel while it is still PENDING (not yet received), by the partner who sent it. */
    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public Parcel updateVendorParcel(String parcelId, VendorParcelRequest request) {
        User vendor = shortly.mandmcorp.dev.shortly.utils.CurrentUser.get();
        Parcel existing = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (!java.util.Objects.equals(existing.getVendorId(), vendor.getPhoneNumber())
                || !java.util.Objects.equals(existing.getCompanyId(), vendor.getCompanyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit parcels you sent");
        }
        if (existing.getParcelStatus() != shortly.mandmcorp.dev.shortly.enums.ParcelStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This parcel has already been received at the station and can't be edited; call the station");
        }
        String keepBarCode = existing.getBarCode();
        boolean barCodeChanged = request.getBarCode() != null && !request.getBarCode().isBlank()
                && !request.getBarCode().trim().equals(keepBarCode);
        VendorParcelRequest check = request;
        if (!barCodeChanged) {
            // don't treat the parcel's own barcode as a duplicate
            check = copyWithoutBarCode(request);
        }
        Parcel updated = prepareVendorParcel(check, vendor, null);
        updated.setParcelId(existing.getParcelId());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setBarCode(barCodeChanged ? resolveBarCode(request.getBarCode()) : keepBarCode);
        updated.setImageUrls(request.getImages() != null && !request.getImages().isEmpty()
                ? r2Service.uploadImages(request.getImages()) : existing.getImageUrls());
        return parcelRepository.save(updated);
    }

    /** Validates a partner parcel and builds it (no images uploaded, no barcode assigned, not saved). */
    private Parcel prepareVendorParcel(VendorParcelRequest request, User vendor, String label) {
        String prefix = label == null ? "" : label + ": ";
        if (request.getReceiverName() == null || request.getReceiverName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "receiver name is required");
        }
        if (request.getRecieverPhoneNumber() == null || request.getRecieverPhoneNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "receiver phone number is required");
        }
        if (request.getDestinationStationId() == null || request.getDestinationStationId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "destination station is required");
        }
        Office destinationOffice = officeRepository.findById(request.getDestinationStationId())
                .filter(o -> java.util.Objects.equals(o.getCompanyId(), vendor.getCompanyId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "destination station not found"));

        if (request.isPOD() && request.getDeliveryFee() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "delivery fee is required when POD is selected");
        }
        try {
            locationValidator.validateDropoff(request.getReceiverLatitude(), request.getReceiverLongitude());
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + e.getReason());
        }
        if (request.getBarCode() != null && !request.getBarCode().isBlank()) {
            String code = request.getBarCode().trim();
            Parcel same = parcelRepository.findByBarCode(code);
            if (same != null) {
                boolean recentOwn = java.util.Objects.equals(same.getVendorId(), vendor.getPhoneNumber())
                        && same.getCreatedAt() != null && System.currentTimeMillis() - same.getCreatedAt() < 24 * 3_600_000L;
                throw new ResponseStatusException(recentOwn ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
                        prefix + (recentOwn ? "you already sent a parcel with barcode " + code + " in the last 24 hours"
                                : "barcode " + code + " already exists"));
            }
        }

        OfficeInfo toOfficeInfo = new OfficeInfo();
        toOfficeInfo.setOfficeId(destinationOffice.getId());
        toOfficeInfo.setOfficeName(destinationOffice.getName());

        Parcel parcel = new Parcel();
        parcel.setReceiverName(request.getReceiverName().trim());
        parcel.setRecieverPhoneNumber(request.getRecieverPhoneNumber());
        parcel.setAlternativePhoneNumber(request.getRecieverAlternativePhoneNumber());
        parcel.setReceiverAddress(request.getReceiverAddress());
        parcel.setParcelDescription(request.getParcelDescription());
        parcel.setParcelWeight(request.getParcelWeight());
        parcel.setNumberOfItems(request.getNumberOfItems());
        parcel.setItemQuantity(request.getItemQuantity());
        parcel.setVendorDeliveryFee(request.getDeliveryFee());
        parcel.setItemCost(request.getItemCost());
        parcel.setPOD(request.isPOD());
        parcel.setVendorName(vendor.getName());
        parcel.setVendorId(vendor.getPhoneNumber());
        parcel.setCompanyId(vendor.getCompanyId());
        parcel.setParcelStatus(shortly.mandmcorp.dev.shortly.enums.ParcelStatus.PENDING);
        parcel.setTypeofParcel(ParcelTypes.TRANSFER);
        parcel.setParcelTransfer(true);
        parcel.setToOfficeId(destinationOffice.getId());
        parcel.setTo(toOfficeInfo);
        parcel.setReceiverLatitude(request.getReceiverLatitude());
        parcel.setReceiverLongitude(request.getReceiverLongitude());
        parcel.setReceiverLocationName(request.getReceiverLocationName());
        parcel.setReceiverPlaceId(request.getReceiverPlaceId());
        parcel.setReceiverLocationSource(request.getReceiverLocationSource());
        if (request.getReceiverLatitude() != null) parcel.setHomeDelivery(true);
        return parcel;
    }

    private Parcel saveVendorParcel(Parcel parcel, VendorParcelRequest request) {
        parcel.setImageUrls(r2Service.uploadImages(request.getImages()));
        parcel.setBarCode(resolveBarCode(request.getBarCode()));
        return parcelRepository.save(parcel);
    }

    private static VendorParcelRequest copyWithoutBarCode(VendorParcelRequest r) {
        VendorParcelRequest c = new VendorParcelRequest();
        org.springframework.beans.BeanUtils.copyProperties(r, c);
        c.setBarCode(null);
        return c;
    }

    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public shortly.mandmcorp.dev.shortly.dto.response.VendorEarningsResponse getVendorEarnings() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User vendor = (User) auth.getPrincipal();
        String phone = vendor.getPhoneNumber();

        Query query = new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("vendorId").is(phone)));
        List<Parcel> allParcels = mongoTemplate.find(query, Parcel.class);

        double amountReady = 0;
        double pendingPayout = 0;
        int failedCount = 0;
        List<shortly.mandmcorp.dev.shortly.dto.response.VendorCollectedParcel> collectedParcels = new ArrayList<>();
        java.util.Map<String, shortly.mandmcorp.dev.shortly.dto.response.VendorStationEarnings> stationMap = new java.util.LinkedHashMap<>();

        for (Parcel p : allParcels) {
            shortly.mandmcorp.dev.shortly.enums.ParcelStatus ps = p.getParcelStatus();
            boolean isFailed = ps == shortly.mandmcorp.dev.shortly.enums.ParcelStatus.FAILED
                    || ps == shortly.mandmcorp.dev.shortly.enums.ParcelStatus.REVERSED;

            if (isFailed) {
                failedCount++;
                continue;
            }

            double parcelAmount = p.getVendorDeliveryFee() + p.getItemCost();
            String stationId = p.getTo() != null ? p.getTo().getOfficeId() : "unknown";
            String stationName = p.getTo() != null ? p.getTo().getOfficeName() : "Unknown";

            shortly.mandmcorp.dev.shortly.dto.response.VendorStationEarnings station =
                stationMap.computeIfAbsent(stationId, k ->
                    shortly.mandmcorp.dev.shortly.dto.response.VendorStationEarnings.builder()
                        .officeId(stationId)
                        .officeName(stationName)
                        .parcelCount(0).deliveredCount(0).collectedAmount(0).totalAmount(0)
                        .build());

            station.setParcelCount(station.getParcelCount() + 1);
            station.setTotalAmount(station.getTotalAmount() + parcelAmount);

            if (p.isDelivered() && p.isVendorPayed()) {
                amountReady += parcelAmount;
                station.setCollectedAmount(station.getCollectedAmount() + parcelAmount);
                station.setDeliveredCount(station.getDeliveredCount() + 1);
            } else  {
                pendingPayout += parcelAmount;
                if(p.isDelivered()) {
                    station.setDeliveredCount(station.getDeliveredCount() + 1);
                }
                collectedParcels.add(
                    shortly.mandmcorp.dev.shortly.dto.response.VendorCollectedParcel.builder()
                        .parcelId(p.getParcelId())
                        .receiverName(p.getReceiverName())
                        .stationName(stationName)
                        .itemCost(p.getItemCost())
                        .deliveryFee(p.getVendorDeliveryFee())
                        .total(parcelAmount)
                        .createdAt(p.getCreatedAt())
                        .build());
            }
            // not yet delivered — parcelCount and totalAmount already updated above, deliveredCount stays 0
        }

        double totalEarnable = amountReady + pendingPayout;
        double collectionRate = totalEarnable > 0
            ? Math.round((amountReady / totalEarnable) * 10000.0) / 100.0
            : 0;

        return shortly.mandmcorp.dev.shortly.dto.response.VendorEarningsResponse.builder()
            .totalEarnable(totalEarnable)
            .amountReady(amountReady)
            .pendingPayout(pendingPayout)
            .failedDeliveriesCount(failedCount)
            .collectionRate(collectionRate)
            .earningsByStation(new ArrayList<>(stationMap.values()))
            .collectedParcels(collectedParcels)
            .build();
    }

    @Override
    @PreAuthorize("hasRole('VENDOR')")
    public Page<Parcel> getVendorParcels(String search, shortly.mandmcorp.dev.shortly.enums.ParcelStatus status, String toOfficeId, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User vendor = (User) auth.getPrincipal();
        String phone = vendor.getPhoneNumber();

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(Criteria.where("vendorId").is(phone));

        if (status != null) {
            criteria.add(Criteria.where("parcelStatus").is(status));
        }

        if (toOfficeId != null && !toOfficeId.isBlank()) {
            criteria.add(Criteria.where("toOfficeId").is(toOfficeId));
        }

        if (search != null && !search.isBlank()) {
            criteria.add(new Criteria().orOperator(
                Criteria.where("_id").is(search),
                Criteria.where("receiverName").regex(search, "i"),
                Criteria.where("recieverPhoneNumber").regex(search, "i")
            ));
        }

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, Parcel.class);

        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);
        return new PageImpl<>(parcels, pageable, total);
    }

    private static final String UCC_GOIL_HUB_OFFICE_ID = "6a5b69da3bf95afbf713ae2f";

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public List<Parcel> markParcelsAsReceived(ParcelReceivedRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        List<Parcel> updated = new ArrayList<>();
        for (String parcelId : request.getParcelIds()) {
            Parcel parcel = parcelRepository.findById(parcelId)
                    .orElseThrow(() -> new EntityNotFound("Parcel not found: " + parcelId));
            assertSameCompany(parcel);
            parcel.setParcelStatus(shortly.mandmcorp.dev.shortly.enums.ParcelStatus.RECEIVED);
            parcel.setOfficeId(officeId);
            Parcel savedParcel = parcelRepository.save(parcel);
            updated.add(savedParcel);
            customerPortalService.sendArrivalSms(savedParcel, arrivalSmsEnabled());

            if (UCC_GOIL_HUB_OFFICE_ID.equals(officeId) && savedParcel.getDriverPhoneNumber() != null
                    && !savedParcel.getDriverPhoneNumber().isBlank()) {
                notification.send(NotificationRequestTemplate.builder()
                        .to(savedParcel.getDriverPhoneNumber())
                        .body(NotificationUtil.generateParcelReceivedAtHubSms(savedParcel.getDriverName()))
                        .build());
            }
        }
        return updated;
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")
    public Parcel addParcel(ParcelRequest parcelRequest) {
      
        locationValidator.validateDropoff(parcelRequest.getReceiverLatitude(), parcelRequest.getReceiverLongitude());
        locationValidator.validatePickup(parcelRequest.getPickupLatitude(), parcelRequest.getPickupLongitude());
        Parcel parcel = parcelMapper.toEntity(parcelRequest);
        parcel.setParcelStatus(shortly.mandmcorp.dev.shortly.enums.ParcelStatus.RECEIVED);
        if ((parcelRequest.getOfficeId() != null) && (!parcelRequest.isParcelTransfer())) {
            Office office = officeRepository.findById(parcelRequest.getOfficeId())
                    .orElseThrow(() -> new EntityNotFound("Office not found"));
            log.info("Office found 1: {}", office.getName());
            parcel.setOfficeId(office.getId());
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                // Use the first office ID from user's office list
                String userOfficeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                    ? user.getOfficeIds().get(0)
                    : null;
                if ((userOfficeId != null) && (!parcelRequest.isParcelTransfer())) {
                    log.info("User office found 2: {}", userOfficeId);
                    parcel.setOfficeId(userOfficeId);
                }
            }
        }
        if(parcelRequest.isHasCalled()) {
            parcel.setHasCalled(true);
        }
        
    if(parcelRequest.getShelfNumber() != null) {
        Shelf shelf = shelfRepository.findById(parcelRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));
        
        parcel.setShelfName(shelf.getName());
        parcel.setShelfId(shelf.getId());
    }
        
        if(parcelRequest.getTypeofParcel() == ParcelTypes.PICKUP && parcelRequest.getRiderId() != null) {
            User rider = userRepository.findById(parcelRequest.getRiderId())
                    .orElseThrow(() -> new EntityNotFound("Rider not found"));
            RiderInfo riderInfo = new RiderInfo();
            riderInfo.setRiderName(rider.getName());
            riderInfo.setRiderPhoneNumber(rider.getPhoneNumber());
            riderInfo.setRiderId(rider.getUserId());
            parcel.setRiderInfo(riderInfo);
        }

            if(parcelRequest.isParcelTransfer()) {
                if(parcelRequest.getFromOfficeId() != null) {
                    Office fromOffice = officeRepository.findById(parcelRequest.getFromOfficeId())
                            .orElseThrow(() -> new EntityNotFound("From office not found"));
                    OfficeInfo from = new OfficeInfo();
                    from.setOfficeId(fromOffice.getId());
                    from.setOfficeName(fromOffice.getName());
                    parcel.setFrom(from);
                }
    
                if(parcelRequest.getToOfficeId() != null) {
                    Office toOffice = officeRepository.findById(parcelRequest.getToOfficeId())
                            .orElseThrow(() -> new EntityNotFound("To office not found"));
                    OfficeInfo to = new OfficeInfo();
                    to.setOfficeId(toOffice.getId());
                    to.setOfficeName(toOffice.getName());
                    parcel.setTo(to);
                }
            }

        applyStationPickupCharge(parcel, parcelRequest);
        // Distance pricing (5c): the server re-quotes home deliveries with a pin and stores its figures.
        deliveryPricingService.applyIntakePricing(parcel, parcelRequest);

        parcel.setImageUrls(r2Service.uploadImages(parcelRequest.getImages()));
        parcel.setBarCode(resolveBarCode(parcelRequest.getBarCode()));

        // Scope the parcel to the logged-in user's company.
        parcel.setCompanyId(loggedInCompanyId());

        // Save the parcel first so it gets its generated ID before building ParcelInfo
        Parcel savedParcel = parcelRepository.save(parcel);
        if (!savedParcel.isParcelTransfer() && savedParcel.getOfficeId() != null
                && savedParcel.getTypeofParcel() != ParcelTypes.PICKUP) {
            customerPortalService.sendArrivalSms(savedParcel, arrivalSmsEnabled());
        }

        // Driver who brought the parcel in: link the driver record and the trip.
        shortly.mandmcorp.dev.shortly.model.Driver driverRecord = resolveIntakeDriver(parcelRequest, savedParcel);
        shortly.mandmcorp.dev.shortly.model.DriverTrip trip = null;
        if (driverRecord != null) {
            Authentication intakeAuth = SecurityContextHolder.getContext().getAuthentication();
            User receivedBy = intakeAuth != null && intakeAuth.getPrincipal() instanceof User u ? u : null;
            trip = driverFeeLineService.attachToTrip(driverRecord, parcelRequest.getTripId(),
                    savedParcel.getVehicleNumber(), savedParcel.getOfficeId(), receivedBy);
            savedParcel.setDriverRecordId(driverRecord.getId());
            savedParcel.setTripId(trip.getId());
            savedParcel = parcelRepository.save(savedParcel);
        }

        //check to see if there is inbound cost
        if(savedParcel.getInboundCost() > 0) {
            if(savedParcel.getDriverPhoneNumber() == null) {
                throw new WrongCredentialsException("Driver phone number is required when inbound cost is provided");
            }
            //get driver phonenumber id format
            
            
            //form parcel info
            ParcelInfo parcelInfo = new ParcelInfo();
            parcelInfo.setParcelId(savedParcel.getParcelId());
            parcelInfo.setDelivered(false);
            parcelInfo.setPayed(false);
            parcelInfo.setReturned(false);
            parcelInfo.setInboudPayed(false);
            parcelInfo.setReceiverName(savedParcel.getReceiverName());
            parcelInfo.setReceiverPhoneNumber(savedParcel.getRecieverPhoneNumber());
            parcelInfo.setAlternativePhoneNumber(savedParcel.getAlternativePhoneNumber());
            parcelInfo.setReceiverAddress(savedParcel.getReceiverAddress());
            ParcelMapper.copyLocation(savedParcel, parcelInfo);
            parcelInfo.setSenderName(savedParcel.getSenderName());
            parcelInfo.setSenderPhoneNumber(savedParcel.getSenderPhoneNumber());
            parcelInfo.setPaymentMethod(savedParcel.getPaymentMethod());
            parcelInfo.setInboundCost(savedParcel.getInboundCost());
            parcelInfo.setDeliveryCost(savedParcel.getDeliveryCost());
            parcelInfo.setStorageCost(savedParcel.getStorageCost());
            parcelInfo.setPickUpCost(savedParcel.getPickUpCost());
            parcelInfo.setParcelWeight(savedParcel.getParcelWeight());
            parcelInfo.setPickedUp(savedParcel.isPickedUp());
            parcelInfo.setHomeDelivery(savedParcel.isHomeDelivery());
            parcelInfo.setPOD(savedParcel.isPOD());
            parcelInfo.setFragile(savedParcel.isFragile());
            parcelInfo.setVehicleNumber(savedParcel.getVehicleNumber());
            parcelInfo.setDriverName(savedParcel.getDriverName());
            parcelInfo.setDriverPhoneNumber(savedParcel.getDriverPhoneNumber());
            parcelInfo.setOfficeId(savedParcel.getOfficeId());
            parcelInfo.setShelfName(savedParcel.getShelfName());
            parcelInfo.setShelfId(savedParcel.getShelfId());
            parcelInfo.setTypeofParcel(savedParcel.getTypeofParcel());
            parcelInfo.setItemCost(savedParcel.getItemCost());
            parcelInfo.setDeliveryAddress(savedParcel.getDeliveryAddress());
            parcelInfo.setDeliveryContactName(savedParcel.getDeliveryContactName());
            parcelInfo.setDeliveryContactPhoneNumber(savedParcel.getDeliveryContactPhoneNumber());
            parcelInfo.setPickupAddress(savedParcel.getPickupAddress());
            parcelInfo.setPickupContactName(savedParcel.getPickupContactName());
            parcelInfo.setPickupContactPhoneNumber(savedParcel.getPickupContactPhoneNumber());
            parcelInfo.setPickupInstructions(savedParcel.getPickupInstructions());
            parcelInfo.setSpecialInstructions(savedParcel.getSpecialInstructions());

            DriverAssignment driverAssignment = new DriverAssignment();
            driverAssignment.setParcelId(savedParcel.getParcelId());
            driverAssignment.setOfficeId(savedParcel.getOfficeId());
            driverAssignment.setCompanyId(savedParcel.getCompanyId());
            driverAssignment.setDriverName(savedParcel.getDriverName());
            driverAssignment.setDriverPhoneNumber(savedParcel.getDriverPhoneNumber());
            driverAssignment.setParcelInfo(parcelInfo);
            driverAssignment.setPayed(false);
            driverAssignment.setAmount(savedParcel.getInboundCost());
            driverFeeLineService.initLine(driverAssignment, driverRecord, trip);

            driverAssignmentRepository.save(driverAssignment);
        }

        return savedParcel;
    }

    @Override
public Parcel updateParcel(String parcelId, ParcelUpdateRequest updateRequest) {

    Parcel parcel = parcelRepository.findById(parcelId)
            .orElseThrow(() -> new WrongCredentialsException("Parcel not found"));
    assertSameCompany(parcel);

    if (updateRequest.getDriverPhoneNumber() != null) {
        parcel.setDriverPhoneNumber(updateRequest.getDriverPhoneNumber());
    }

    if (updateRequest.getPickedUp()!= null) {
        parcel.setPickedUp(updateRequest.getPickedUp());
    }
    if (updateRequest.getDriverName() != null) {
        parcel.setDriverName(updateRequest.getDriverName());
    }

    if (updateRequest.getVehicleNumber() != null) {
        parcel.setVehicleNumber(updateRequest.getVehicleNumber());
    }

    if (updateRequest.getSenderPhoneNumber() != null) {
        parcel.setSenderPhoneNumber(updateRequest.getSenderPhoneNumber());
    }

    if (updateRequest.getSenderName() != null) {
        parcel.setSenderName(updateRequest.getSenderName());
    }

    if (updateRequest.getReceiverAddress() != null) {
        parcel.setReceiverAddress(updateRequest.getReceiverAddress());
    }

    if (updateRequest.getReceiverName() != null) {
        parcel.setReceiverName(updateRequest.getReceiverName());
    }

    if (updateRequest.getRecieverPhoneNumber() != null) {
        parcel.setRecieverPhoneNumber(updateRequest.getRecieverPhoneNumber());
    }

    if (updateRequest.getAlternativePhoneNumber() != null) {
        parcel.setAlternativePhoneNumber(updateRequest.getAlternativePhoneNumber());
    }

    if (updateRequest.getParcelDescription() != null) {
        parcel.setParcelDescription(updateRequest.getParcelDescription());
    }

    if (updateRequest.getIsPOD() != null) {
        parcel.setPOD(updateRequest.getIsPOD());
    }

    if (updateRequest.getIsDelivered() != null) {
        parcel.setDelivered(updateRequest.getIsDelivered());
    }

    if (updateRequest.getIsParcelAssigned() != null) {
        parcel.setParcelAssigned(updateRequest.getIsParcelAssigned());
    }

    if (updateRequest.getInboundCost() != null) {
        parcel.setInboundCost(updateRequest.getInboundCost());
    }

    if (updateRequest.getPickUpCost() != null) {
        parcel.setPickUpCost(updateRequest.getPickUpCost());
    }

    if (updateRequest.getIsFragile() != null) {
        parcel.setFragile(updateRequest.getIsFragile());
    }

    if (updateRequest.getDeliveryCost() != null) {
        parcel.setDeliveryCost(updateRequest.getDeliveryCost());
    }

    if (updateRequest.getStorageCost() != null) {
        parcel.setStorageCost(updateRequest.getStorageCost());
    }

    if (updateRequest.getHomeDelivery() != null) {
        parcel.setHomeDelivery(updateRequest.getHomeDelivery());
    }

    if (updateRequest.getHasCalled() != null) {
        parcel.setHasCalled(updateRequest.getHasCalled());
    }

    if (updateRequest.getShelfNumber() != null) {
        Shelf shelf = shelfRepository.findById(updateRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));
        parcel.setShelfId(shelf.getId());
        parcel.setShelfName(shelf.getName());
    }

    // Payment and shelf info
    if (updateRequest.getPaymentMethod() != null) {
        parcel.setPaymentMethod(updateRequest.getPaymentMethod());
    }

    if (updateRequest.getShelfName() != null) {
        parcel.setShelfName(updateRequest.getShelfName());
    }

    if (updateRequest.getInboudPayed() != null) {
        parcel.setInboudPayed(updateRequest.getInboudPayed());
    }

    if (updateRequest.getShelfId() != null) {
        parcel.setShelfId(updateRequest.getShelfId());
    }

    // Parcel type
    if (updateRequest.getTypeofParcel() != null) {
        parcel.setTypeofParcel(updateRequest.getTypeofParcel());
    }

    // Online order fields
    if (updateRequest.getItemCost() != null) {
        parcel.setItemCost(updateRequest.getItemCost());
    }

    if (updateRequest.getIsItemOwnerPaid() != null) {
        parcel.setItemOwnerPaid(updateRequest.getIsItemOwnerPaid());
    }

    if (updateRequest.getHasArrivedAtOffice() != null) {
        parcel.setHasArrivedAtOffice(updateRequest.getHasArrivedAtOffice());
    }

    if (updateRequest.getFromOfficeId() != null) {
        Office fromOffice = officeRepository.findById(updateRequest.getFromOfficeId())
                .orElseThrow(() -> new EntityNotFound("From office not found"));
        OfficeInfo from = new OfficeInfo();
        from.setOfficeId(fromOffice.getId());
        from.setOfficeName(fromOffice.getName());
        parcel.setFrom(from);
        parcel.setFromOfficeId(fromOffice.getId());
    }

    if (updateRequest.getToOfficeId() != null) {
        Office toOffice = officeRepository.findById(updateRequest.getToOfficeId())
                .orElseThrow(() -> new EntityNotFound("To office not found"));
        OfficeInfo to = new OfficeInfo();
        to.setOfficeId(toOffice.getId());
        to.setOfficeName(toOffice.getName());
        parcel.setTo(to);
        parcel.setToOfficeId(toOffice.getId());
    }

    if (updateRequest.getRiderId() != null) {
        User rider = userRepository.findById(updateRequest.getRiderId())
                .orElseThrow(() -> new EntityNotFound("Rider not found"));
        parcel.setRiderId(rider.getUserId());
        RiderInfo riderInfo = new RiderInfo();
        riderInfo.setRiderName(rider.getName());
        riderInfo.setRiderPhoneNumber(rider.getPhoneNumber());
        riderInfo.setRiderId(rider.getUserId());
        parcel.setRiderInfo(riderInfo);
    }

    // Pickup fields
    if (updateRequest.getPickupAddress() != null) {
        parcel.setPickupAddress(updateRequest.getPickupAddress());
    }

    if (updateRequest.getPickupContactName() != null) {
        parcel.setPickupContactName(updateRequest.getPickupContactName());
    }

    if (updateRequest.getPickupContactPhoneNumber() != null) {
        parcel.setPickupContactPhoneNumber(updateRequest.getPickupContactPhoneNumber());
    }

    if (updateRequest.getPickupInstructions() != null) {
        parcel.setPickupInstructions(updateRequest.getPickupInstructions());
    }

    // Delivery fields
    if (updateRequest.getDeliveryAddress() != null) {
        parcel.setDeliveryAddress(updateRequest.getDeliveryAddress());
    }

    if (updateRequest.getDeliveryContactName() != null) {
        parcel.setDeliveryContactName(updateRequest.getDeliveryContactName());
    }

    if (updateRequest.getDeliveryContactPhoneNumber() != null) {
        parcel.setDeliveryContactPhoneNumber(updateRequest.getDeliveryContactPhoneNumber());
    }

    if (updateRequest.getSpecialInstructions() != null) {
        parcel.setSpecialInstructions(updateRequest.getSpecialInstructions());
    }

    // Delivery location: absent = leave as is, explicit null = clear.
    boolean locationChanged = false;
    if (updateRequest.isReceiverLocationProvided()) {
        locationValidator.validateDropoff(updateRequest.getReceiverLatitude(), updateRequest.getReceiverLongitude());
        locationChanged = !java.util.Objects.equals(parcel.getReceiverLatitude(), updateRequest.getReceiverLatitude())
                || !java.util.Objects.equals(parcel.getReceiverLongitude(), updateRequest.getReceiverLongitude());
        parcel.setReceiverLatitude(updateRequest.getReceiverLatitude());
        parcel.setReceiverLongitude(updateRequest.getReceiverLongitude());
        parcel.setReceiverLocationName(updateRequest.getReceiverLocationName());
        parcel.setReceiverPlaceId(updateRequest.getReceiverPlaceId());
        parcel.setReceiverLocationSource(updateRequest.getReceiverLocationSource());
    }

    if (updateRequest.isPickupLocationProvided()) {
        locationValidator.validatePickup(updateRequest.getPickupLatitude(), updateRequest.getPickupLongitude());
        parcel.setPickupLatitude(updateRequest.getPickupLatitude());
        parcel.setPickupLongitude(updateRequest.getPickupLongitude());
        parcel.setPickupLocationName(updateRequest.getPickupLocationName());
        parcel.setPickupPlaceId(updateRequest.getPickupPlaceId());
        parcel.setPickupLocationSource(updateRequest.getPickupLocationSource());
    }

    Parcel savedParcel = parcelRepository.save(parcel);

    if (updateRequest.isReceiverLocationProvided() || updateRequest.isPickupLocationProvided()) {
        syncLocationToOpenAssignments(savedParcel, locationChanged);
    }

    if (savedParcel.getInboundCost() > 0) {
        Query reconcQuery = new Query(Criteria.where("parcels.parcelId").is(parcelId));
        DriverReconcilation reconcilation = mongoTemplate.findOne(reconcQuery, DriverReconcilation.class);

        if (reconcilation != null) {
            for (ParcelInfo pi : reconcilation.getParcels()) {
                if (parcelId.equals(pi.getParcelId())) {
                    double oldInboundCost = pi.getInboundCost();

                    pi.setReceiverName(savedParcel.getReceiverName());
                    pi.setReceiverPhoneNumber(savedParcel.getRecieverPhoneNumber());
                    pi.setAlternativePhoneNumber(savedParcel.getAlternativePhoneNumber());
                    pi.setReceiverAddress(savedParcel.getReceiverAddress());
                    ParcelMapper.copyLocation(savedParcel, pi);
                    pi.setSenderName(savedParcel.getSenderName());
                    pi.setSenderPhoneNumber(savedParcel.getSenderPhoneNumber());
                    pi.setPaymentMethod(savedParcel.getPaymentMethod());
                    pi.setInboundCost(savedParcel.getInboundCost());
                    pi.setDeliveryCost(savedParcel.getDeliveryCost());
                    pi.setStorageCost(savedParcel.getStorageCost());
                    pi.setPickUpCost(savedParcel.getPickUpCost());
                    pi.setDelivered(savedParcel.isDelivered());
                    pi.setHomeDelivery(savedParcel.isHomeDelivery());
                    pi.setPOD(savedParcel.isPOD());
                    pi.setFragile(savedParcel.isFragile());
                    pi.setPickedUp(savedParcel.isPickedUp());
                    pi.setInboudPayed(savedParcel.isInboudPayed());
                    pi.setDriverName(savedParcel.getDriverName());
                    pi.setDriverPhoneNumber(savedParcel.getDriverPhoneNumber());
                    pi.setVehicleNumber(savedParcel.getVehicleNumber());
                    pi.setOfficeId(savedParcel.getOfficeId());
                    pi.setShelfName(savedParcel.getShelfName());
                    pi.setShelfId(savedParcel.getShelfId());
                    pi.setTypeofParcel(savedParcel.getTypeofParcel());
                    pi.setItemCost(savedParcel.getItemCost());
                    pi.setPickupAddress(savedParcel.getPickupAddress());
                    pi.setPickupContactName(savedParcel.getPickupContactName());
                    pi.setPickupContactPhoneNumber(savedParcel.getPickupContactPhoneNumber());
                    pi.setPickupInstructions(savedParcel.getPickupInstructions());
                    pi.setDeliveryAddress(savedParcel.getDeliveryAddress());
                    pi.setDeliveryContactName(savedParcel.getDeliveryContactName());
                    pi.setDeliveryContactPhoneNumber(savedParcel.getDeliveryContactPhoneNumber());
                    pi.setSpecialInstructions(savedParcel.getSpecialInstructions());

                    reconcilation.setTotalAmount(
                            reconcilation.getTotalAmount() - oldInboundCost + savedParcel.getInboundCost());
                    break;
                }
            }

            driverReconcilationRepository.save(reconcilation);
        }
    }

    return savedParcel;
}


    /**
     * The driver record for a parcel at intake: the given driverId (must be the same company), else
     * found or created from the typed driver phone number. Never blocks intake on lookup problems
     * other than an unknown driverId.
     */
    private shortly.mandmcorp.dev.shortly.model.Driver resolveIntakeDriver(ParcelRequest request, Parcel parcel) {
        if (request.getDriverId() != null && !request.getDriverId().isBlank()) {
            shortly.mandmcorp.dev.shortly.model.Driver d = mongoTemplate.findById(request.getDriverId(),
                    shortly.mandmcorp.dev.shortly.model.Driver.class);
            if (d == null || !java.util.Objects.equals(d.getCompanyId(), parcel.getCompanyId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver not found");
            }
            if (parcel.getDriverName() == null) parcel.setDriverName(d.getName());
            if (parcel.getDriverPhoneNumber() == null && !d.getPhoneNumbers().isEmpty()) {
                parcel.setDriverPhoneNumber(d.getPhoneNumbers().get(0));
            }
            if (parcel.getOfficeId() != null && !d.getOfficeIds().contains(parcel.getOfficeId())) {
                d.getOfficeIds().add(parcel.getOfficeId());
                mongoTemplate.save(d);
            }
            return d;
        }
        if (parcel.getDriverPhoneNumber() == null || parcel.getDriverPhoneNumber().isBlank()) return null;
        try {
            return driverFeeLineService.ensureDriver(parcel.getCompanyId(), parcel.getOfficeId(),
                    parcel.getDriverName(), parcel.getDriverPhoneNumber(), parcel.getVehicleNumber());
        } catch (Exception e) {
            log.warn("Could not link driver for parcel {}: {}", parcel.getParcelId(), e.getMessage());
            return null;
        }
    }

    /**
     * Copies a changed drop-off pin onto the parcel's snapshot in any assignment where it is still
     * pending, so the rider app sees the corrected pin. When the coordinates moved, the assignment's
     * route plan is marked stale.
     */
    private void syncLocationToOpenAssignments(Parcel parcel, boolean coordinatesChanged) {
        Query query = new Query(Criteria.where("parcels.parcelId").is(parcel.getParcelId()));
        List<shortly.mandmcorp.dev.shortly.model.DeliveryAssignments> assignments =
                mongoTemplate.find(query, shortly.mandmcorp.dev.shortly.model.DeliveryAssignments.class);
        for (shortly.mandmcorp.dev.shortly.model.DeliveryAssignments assignment : assignments) {
            boolean touched = false;
            for (ParcelInfo info : assignment.getParcels()) {
                if (parcel.getParcelId().equals(info.getParcelId()) && !info.isDelivered() && !info.isReturned()) {
                    ParcelMapper.copyLocation(parcel, info);
                    touched = true;
                }
            }
            if (!touched) continue;
            if (coordinatesChanged && assignment.getRoutePlan() != null
                    && assignment.getRoutePlan().getStopOrder() != null
                    && assignment.getRoutePlan().getStopOrder().contains(parcel.getParcelId())) {
                assignment.getRoutePlan().setStale(true);
            }
            mongoTemplate.save(assignment);
        }
    }

    /**
     * Pickup requests that don't send pickUpCost fall back to the station's pickup charge when the
     * station charges for pickups. The amount is kept on the parcel as charged.
     */
    private void applyStationPickupCharge(Parcel parcel, ParcelRequest request) {
        if (request.getTypeofParcel() != ParcelTypes.PICKUP || request.getPickUpCost() != null) return;
        if (parcel.getOfficeId() == null) return;
        officeRepository.findById(parcel.getOfficeId()).ifPresent(office -> {
            if (Boolean.TRUE.equals(office.getPickupChargeEnabled()) && office.getPickupChargeAmount() != null) {
                parcel.setPickUpCost(office.getPickupChargeAmount());
            }
        });
    }

    @Override
    public Page<Parcel> searchParcels(
        Boolean isPOD,
        Boolean isDelivered,
        Boolean isParcelAssigned,
        String officeId,
        String driverPhoneNumber,
        Boolean hasCalled,
        String search,
        Pageable pageable,
        boolean isFrontDesk) {

    if (isFrontDesk) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            // Get the first office ID from user's office list
            if (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty()) {
                officeId = user.getOfficeIds().get(0);
            }
        }
    }
    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();
    criteria.add(companyScope());

    if (isPOD != null) {
        criteria.add(Criteria.where("isPOD").is(isPOD));
    }

    if (isDelivered != null) {
        criteria.add(Criteria.where("isDelivered").is(isDelivered));
    }

    if (isParcelAssigned != null) {
        criteria.add(Criteria.where("isParcelAssigned").is(isParcelAssigned));
    }

    if (hasCalled != null) {
        criteria.add(Criteria.where("hasCalled").is(hasCalled));
    }

    if (officeId != null) {
        criteria.add(new Criteria().orOperator(
            Criteria.where("officeId").is(officeId),
            new Criteria().andOperator(
                Criteria.where("toOfficeId").is(officeId),
                Criteria.where("hasArrivedAtOffice").is(true)
            )
        ));
    }

    if (driverPhoneNumber != null) {
        criteria.add(Criteria.where("driverPhoneNumber").is(driverPhoneNumber));
    }

    // Single free-text search: case-insensitive "contains" across the common parcel fields.
    if (search != null && !search.isBlank()) {
        String term = java.util.regex.Pattern.quote(search.trim());
        criteria.add(new Criteria().orOperator(
            // Parcel ID: exact id, plus a contains match for string ids
            Criteria.where("_id").is(search.trim()),
            Criteria.where("_id").regex(term, "i"),
            Criteria.where("senderName").regex(term, "i"),
            Criteria.where("senderPhoneNumber").regex(term, "i"),
            Criteria.where("receiverName").regex(term, "i"),
            Criteria.where("recieverPhoneNumber").regex(term, "i"),
            Criteria.where("alternativePhoneNumber").regex(term, "i"),
            Criteria.where("receiverAddress").regex(term, "i"),
            Criteria.where("driverName").regex(term, "i"),
            Criteria.where("parcelDescription").regex(term, "i"),
            Criteria.where("barCode").regex(term, "i")
        ));
    }

    // Apply criteria if any
    if (!criteria.isEmpty()) {
        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    // Apply default sorting BEFORE counting and pagination
    org.springframework.data.domain.Sort sort;
    if (pageable.getSort().isSorted()) {
        sort = pageable.getSort();
    } else {
        sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
    }
    query.with(sort);

    // Count total documents matching criteria
    long total = mongoTemplate.count(query, Parcel.class);

    // Apply pagination
    query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
    query.limit(pageable.getPageSize());

    // Execute query
    List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

    return new PageImpl<>(parcels, pageable, total);
}

    

    public UserResponse changeOffice(String officeId) {
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new EntityNotFound("Office not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Add the new office to the user's office list if not already present
        if (user.getOfficeIds() == null) {
            user.setOfficeIds(new java.util.ArrayList<>());
        }
        if (!user.getOfficeIds().contains(office.getId())) {
            user.getOfficeIds().add(office.getId());
        }
        userRepository.save(user);

        return new UserResponse("Office changed successfully", user.getPhoneNumber());
    }

    @Override
    public List<Parcel> getParcelsByDriverId(String driverId, boolean isPOD, String inboundPayed) {

        return null;

    }

    @Override
    //hasrole frontedesk, manager, admin
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")    
    public Page<Parcel> getOnlineParcelsThatareMeantToBePayed(Pageable pageable) {
            Query query = new Query();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User user)) {
                throw new WrongCredentialsException("User not authenticated");
            }  
            List<Criteria> criteria = new ArrayList<>();

            criteria.add(companyScope());
            criteria.add(Criteria.where("isItemOwnerPaid").is(false));
            criteria.add(Criteria.where("typeofParcel").is(ParcelTypes.ONLINE));
            criteria.add(Criteria.where("isDelivered").is(true));
            String officeId = shortly.mandmcorp.dev.shortly.utils.CurrentUser.officeId(user);
            if (officeId == null) return new PageImpl<>(new ArrayList<>(), pageable, 0);
            criteria.add(Criteria.where("toOfficeId").is(officeId));
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
            org.springframework.data.domain.Sort sort;
            if (pageable.getSort().isSorted()) {
                sort = pageable.getSort();
            } else {
                sort = org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
            }
            query.with(sort);

            // Count total documents matching criteria
            long total = mongoTemplate.count(query, Parcel.class);

            // Apply pagination
            query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
            query.limit(pageable.getPageSize());

            // Execute query
            List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

            return new PageImpl<>(parcels, pageable, total);
        }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")
    public Page<Parcel> getTransferParcelsInTransit(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(Criteria.where("parcelTransfer").is(true));
        criteria.add(Criteria.where("toOfficeId").is(officeId));
        criteria.add(Criteria.where("hasArrivedAtOffice").is(false));

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, Parcel.class), pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")
    public Page<Parcel> getOnlineParcelsArrived(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(Criteria.where("typeofParcel").is(ParcelTypes.ONLINE));
        criteria.add(Criteria.where("toOfficeId").is(officeId));
        criteria.add(Criteria.where("hasArrivedAtOffice").is(true));

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, Parcel.class), pageable, total);
    }


    @Override
@PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")
    public Page<Parcel> getTransferOutgoing(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(Criteria.where("parcelTransfer").is(true));
        criteria.add(Criteria.where("fromOfficeId").is(officeId));
        criteria.add(Criteria.where("hasArrivedAtOffice").is(false));

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, Parcel.class), pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")
    public void deleteParcel(String parcelId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found"));
        assertSameCompany(parcel);
        driverFeeLineService.markNotPayable(parcelId);
        parcelRepository.delete(parcel);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public Parcel markParcelAsArrived(String parcelId, String shelfId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found"));
        assertSameCompany(parcel);
        Shelf shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));

        parcel.setHasArrivedAtOffice(true);
        parcel.setShelfId(shelfId);
        parcel.setShelfName(shelf.getName());
        parcel.setOfficeId(officeId);
        Parcel arrived = parcelRepository.save(parcel);
        customerPortalService.sendArrivalSms(arrived, arrivalSmsEnabled());
        return arrived;
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN', 'VENDOR')")
    public Page<Parcel> getHomeDeliveryParcels(Pageable pageable) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }


        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(companyScope());

        criteria.add(Criteria.where("homeDelivery").is(true));

        criteria.add(Criteria.where("isDelivered").is(false));

        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        }
        query.with(sort);

        // Count total documents matching criteria
        long total = mongoTemplate.count(query, Parcel.class);

        // Apply pagination
        //query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(300); //pageable.getPageSize());

        // Execute query
        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

        return new PageImpl<>(parcels, pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CALLCENTER')")
    public Page<Parcel> getUncalledParcels(Pageable pageable) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }


        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(companyScope());

        criteria.add(new Criteria().orOperator(
                Criteria.where("hasCallCenterSpokenToClient").is(false),
                Criteria.where("hasCallCenterSpokenToClient").isNull()
        ));

        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        }
        query.with(sort);

        // Count total documents matching criteria
        long total = mongoTemplate.count(query, Parcel.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

        return new PageImpl<>(parcels, pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CALLCENTER')")
    public Page<Parcel> getUncalledCallCenterParcels(String officeId, Pageable pageable) {
        Criteria notCalled = new Criteria().orOperator(
                Criteria.where("hasCallCenterSpokenToClient").is(false),
                Criteria.where("hasCallCenterSpokenToClient").isNull()
        );

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(notCalled);
        if (officeId != null && !officeId.isBlank()) {
            criteria.add(Criteria.where("officeId").is(officeId));
        }

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);

        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

        return new PageImpl<>(parcels, pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CALLCENTER')")
    public Parcel updateCallCenterOutcome(String parcelId, CallCenterUpdateRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        User caller = (User) auth.getPrincipal();

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found"));
        assertSameCompany(parcel);

        parcel.setCallOutCome(request.getCallOutCome());
        parcel.setCallCenterRemark(request.getRemark());

        if (request.getCallOutCome() == CallCenterCallOutCome.REACHED) {
            parcel.setHasCallCenterSpokenToClient(true);

            if (Boolean.TRUE.equals(request.getHomeDelivered())) {
                parcel.setHomeDelivery(true);
                if (request.getHomeDeliveryAddress() != null) {
                    parcel.setReceiverAddress(request.getHomeDeliveryAddress());
                }
                if (request.getHomeDeliveryPhoneNumber() != null) {
                    parcel.setRecieverPhoneNumber(request.getHomeDeliveryPhoneNumber());
                }
            }
            parcel.setCallerName(caller.getName());
            parcel.setCallerPhoneNumber(caller.getPhoneNumber());
            parcel.setCallCenterRemark(request.getRemark());
            parcel.setNotes(request.getNotes());
            
            Parcel savedParcel = parcelRepository.save(parcel);

            return savedParcel;
        }

        return parcelRepository.save(parcel);
    }

    @Override
    public CallCenterStatsResponse getCallCenterStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        long startOfYesterday = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endOfYesterday = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        Criteria deliveredYesterday = new Criteria().andOperator(
                companyScope(),
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday)
        );

        long totalDeliveredYesterday = mongoTemplate.count(new Query(deliveredYesterday), Parcel.class);

        long reached = mongoTemplate.count(new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday),
                Criteria.where("callOutCome").is(CallCenterCallOutCome.REACHED)
        )), Parcel.class);

        long unreachable = mongoTemplate.count(new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday),
                Criteria.where("callOutCome").is(CallCenterCallOutCome.UNREACHABLE)
        )), Parcel.class);

        long notCalled = mongoTemplate.count(new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday),
                Criteria.where("hasCallCenterSpokenToClient").is(false),
                Criteria.where("callOutCome").isNull()
        )), Parcel.class);

        return CallCenterStatsResponse.builder()
                .totalDeliveredYesterday(totalDeliveredYesterday)
                .reached(reached)
                .unreachable(unreachable)
                .notCalled(notCalled)
                .build();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public CallerStatsResponse getCallerStats(String callerPhoneNumber, String period) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(companyScope());
        criteria.add(Criteria.where("callerPhoneNumber").is(callerPhoneNumber));

        if ("month".equalsIgnoreCase(period)) {
            LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
            long startMillis = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            criteria.add(Criteria.where("createdAt").gte(startMillis));
        }

        Query baseQuery = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        Query homeQuery = new Query(new Criteria().andOperator(
                new ArrayList<>(criteria) {{
                    add(Criteria.where("homeDelivered").is(true));
                }}.toArray(new Criteria[0])
        ));

        Query nonHomeQuery = new Query(new Criteria().andOperator(
                new ArrayList<>(criteria) {{
                    add(Criteria.where("homeDelivered").is(false));
                }}.toArray(new Criteria[0])
        ));

        long totalCalls = mongoTemplate.count(baseQuery, CalledParcelInfo.class);
        long homeDeliveredCalls = mongoTemplate.count(homeQuery, CalledParcelInfo.class);
        long nonHomeDeliveredCalls = mongoTemplate.count(nonHomeQuery, CalledParcelInfo.class);

        return CallerStatsResponse.builder()
                .callerPhoneNumber(callerPhoneNumber)
                .period("month".equalsIgnoreCase(period) ? "month" : "all")
                .totalCalls(totalCalls)
                .homeDeliveredCalls(homeDeliveredCalls)
                .nonHomeDeliveredCalls(nonHomeDeliveredCalls)
                .build();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Page<ParcelSystemLog> getParcelSystemLogs(String officeId, String parcelId, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(companyScope());

        if (officeId != null && !officeId.isBlank()) {
            query.addCriteria(Criteria.where("officeId").is(officeId));
        }

        if (parcelId != null && !parcelId.isBlank()) {
            query.addCriteria(Criteria.where("parcelId").is(parcelId));
        }

        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, ParcelSystemLog.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, ParcelSystemLog.class), pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public ParcelSystemLog pickedUp(PickedUpRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                ? user.getOfficeIds().get(0) : null;

        Parcel parcel = parcelRepository.findById(request.getParcelId())
                .orElseThrow(() -> new EntityNotFound("Parcel not found"));
        assertSameCompany(parcel);

        // Counter payment (Part L): validates, 409 if already recorded; null for old clients.
        shortly.mandmcorp.dev.shortly.model.CounterCollection collection =
                counterService.recordPickup(parcel, request, user, officeId);

        parcel.setPickedUp(true);
        Parcel savedParcel = parcelRepository.save(parcel);
        // The customer has paid: the driver's fee becomes payable (ON_COLLECTION).
        driverFeeLineService.markPickedUp(parcel.getParcelId());
        DriverAssignment driverAssignment = parcel.getInboundCost() > 0
                ? driverAssignmentRepository.findByParcelId(parcel.getParcelId()).orElse(null) : null;
        if(driverAssignment != null) {
            driverAssignment.setDelivered(true);
            driverAssignmentRepository.save(driverAssignment);
            if (parcel.getPickUpCost() > 0 && driverAssignment.getDriverPhoneNumber() != null) {
                String smsBody = NotificationUtil.generateParcelPickedUpDriverSms(
                        driverAssignment.getDriverName(),
                        parcel.getParcelId(),
                        parcel.getPickUpCost());
                notification.send(NotificationRequestTemplate.builder()
                        .to(driverAssignment.getDriverPhoneNumber())
                        .body(smsBody)
                        .build());
            }
        }   
        ParcelInfo parcelInfo = new ParcelInfo();
        parcelInfo.setParcelId(savedParcel.getParcelId());
        parcelInfo.setReceiverName(savedParcel.getReceiverName());
        parcelInfo.setReceiverPhoneNumber(savedParcel.getRecieverPhoneNumber());
        parcelInfo.setAlternativePhoneNumber(savedParcel.getAlternativePhoneNumber());
        parcelInfo.setReceiverAddress(savedParcel.getReceiverAddress());
        parcelInfo.setSenderName(savedParcel.getSenderName());
        parcelInfo.setSenderPhoneNumber(savedParcel.getSenderPhoneNumber());
        parcelInfo.setPaymentMethod(savedParcel.getPaymentMethod());
        parcelInfo.setInboundCost(savedParcel.getInboundCost());
        parcelInfo.setDeliveryCost(savedParcel.getDeliveryCost());
        parcelInfo.setStorageCost(savedParcel.getStorageCost());
        parcelInfo.setPickUpCost(savedParcel.getPickUpCost());
        parcelInfo.setParcelWeight(savedParcel.getParcelWeight());
        parcelInfo.setDelivered(savedParcel.isDelivered());
        parcelInfo.setHomeDelivery(savedParcel.isHomeDelivery());
        parcelInfo.setPOD(savedParcel.isPOD());
        parcelInfo.setFragile(savedParcel.isFragile());
        parcelInfo.setPickedUp(savedParcel.isPickedUp());
        parcelInfo.setInboudPayed(savedParcel.isInboudPayed());
        parcelInfo.setDriverName(savedParcel.getDriverName());
        parcelInfo.setDriverPhoneNumber(savedParcel.getDriverPhoneNumber());
        parcelInfo.setVehicleNumber(savedParcel.getVehicleNumber());
        parcelInfo.setOfficeId(savedParcel.getOfficeId());
        parcelInfo.setShelfName(savedParcel.getShelfName());
        parcelInfo.setShelfId(savedParcel.getShelfId());
        parcelInfo.setTypeofParcel(savedParcel.getTypeofParcel());
        parcelInfo.setItemCost(savedParcel.getItemCost());
        parcelInfo.setPickupAddress(savedParcel.getPickupAddress());
        parcelInfo.setPickupContactName(savedParcel.getPickupContactName());
        parcelInfo.setPickupContactPhoneNumber(savedParcel.getPickupContactPhoneNumber());
        parcelInfo.setPickupInstructions(savedParcel.getPickupInstructions());
        parcelInfo.setDeliveryAddress(savedParcel.getDeliveryAddress());
        parcelInfo.setDeliveryContactName(savedParcel.getDeliveryContactName());
        parcelInfo.setDeliveryContactPhoneNumber(savedParcel.getDeliveryContactPhoneNumber());
        parcelInfo.setSpecialInstructions(savedParcel.getSpecialInstructions());

        ParcelSystemLog log = new ParcelSystemLog();

        log.setParcelId(savedParcel.getParcelId());
        log.setParcelInfo(parcelInfo);
        log.setPickUpTime(java.time.Instant.now().toString());
        log.setWhoPickedUpName(request.getWhoPickedUpName());
        log.setWhoPickedUpTelephoneNumber(request.getWhoPickedUpTelephoneNumber());
        log.setFrontDeskPersonellName(user.getName());
        log.setFrontDeskPersonellPhoneNumber(user.getPhoneNumber());
        log.setOfficeId(officeId);
        log.setCompanyId(savedParcel.getCompanyId());
        log.setCounterPayment(savedParcel.getCounterPayment());

        return parcelSystemLogRepository.save(log);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CALLCENTER')")
    public Page<Parcel> getDeliveredUncalledParcels(String officeId, Pageable pageable) {
        long startOfYesterday = LocalDate.now().minusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        Query query = new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("officeId").is(officeId),
                Criteria.where("isDelivered").is(true),
                Criteria.where("createdAt").gte(startOfYesterday),
                new Criteria().orOperator(
                        Criteria.where("hasCallCenterSpokenToClient").is(false),
                        Criteria.where("hasCallCenterSpokenToClient").isNull()
                )
        ));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, Parcel.class), pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CALLCENTER')")
    public Page<Parcel> getNotDeliveredUncalledParcels(String officeId, Pageable pageable) {
        Query query = new Query(new Criteria().andOperator(
                companyScope(),
                Criteria.where("officeId").is(officeId),
                Criteria.where("isDelivered").is(false),
                new Criteria().orOperator(
                        Criteria.where("hasCallCenterSpokenToClient").is(false),
                        Criteria.where("hasCallCenterSpokenToClient").isNull()
                )
        ));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        return new PageImpl<>(mongoTemplate.find(query, Parcel.class), pageable, total);
    }
}