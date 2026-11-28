package shortly.mandmcorp.dev.shortly.service.customer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.response.CustomerViews;
import shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.CustomerParcel;
import shortly.mandmcorp.dev.shortly.dto.response.CustomerViews.OfficeView;
import shortly.mandmcorp.dev.shortly.enums.LocationSource;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.model.CustomerDeliveryRequest;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable;
import shortly.mandmcorp.dev.shortly.model.DeliveryQuote;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationInterface;
import shortly.mandmcorp.dev.shortly.service.notification.NotificationRequestTemplate;
import shortly.mandmcorp.dev.shortly.service.pricing.DeliveryPricingRules;
import shortly.mandmcorp.dev.shortly.service.pricing.DeliveryPricingService;
import shortly.mandmcorp.dev.shortly.utils.DeliveryLocationValidator;
import shortly.mandmcorp.dev.shortly.utils.Money;
import shortly.mandmcorp.dev.shortly.utils.PhoneNumberUtils;

/**
 * The public customer page (/receive): a safe parcel view, SMS link tokens, one-time codes, and
 * delivery booking paid with Paystack (Part 5b). Nothing here returns staff-only fields.
 */
@Service
@Slf4j
public class CustomerPortalService {

    private static final long QUOTE_VALID_MS = 30 * 60_000L;
    private static final long QUOTE_KEEP_MS = 30L * 86_400_000L;

    private final MongoTemplate mongoTemplate;
    private final OfficeRepository officeRepository;
    private final CustomerLinkService links;
    private final DeliveryPricingService pricing;
    private final DeliveryLocationValidator locationValidator;
    private final NotificationInterface notification;
    private final WebClient webClient;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final String paystackSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomerPortalService(MongoTemplate mongoTemplate, OfficeRepository officeRepository, CustomerLinkService links,
                                 DeliveryPricingService pricing, DeliveryLocationValidator locationValidator,
                                 @Qualifier("smsNotification") NotificationInterface notification, WebClient webClient,
                                 org.springframework.transaction.support.TransactionTemplate transactionTemplate,
                                 @Value("${shortly.paystack.secret-key:}") String paystackSecret) {
        this.mongoTemplate = mongoTemplate;
        this.officeRepository = officeRepository;
        this.links = links;
        this.pricing = pricing;
        this.locationValidator = locationValidator;
        this.notification = notification;
        this.webClient = webClient;
        this.transactionTemplate = transactionTemplate;
        this.paystackSecret = paystackSecret == null ? "" : paystackSecret.trim();
    }

    // ================================================================== views

    /** GET /api-user/parcel-search: the phone's parcels not yet delivered, customer view only. */
    public List<CustomerParcel> search(String phone) {
        return parcelsForPhone(phone, false).stream().map(this::view).toList();
    }

    /** GET /api-user/customer-links/{token}. */
    public CustomerViews.LinkResponse link(String token) {
        String phone = links.phoneFor(token);
        List<Parcel> parcels = parcelsForPhone(phone, true);
        String name = parcels.stream().map(Parcel::getReceiverName).filter(Objects::nonNull).findFirst().orElse(null);
        return new CustomerViews.LinkResponse(phone, name, parcels.stream().map(this::view).toList());
    }

    /** Customer view of one parcel, with the station holding it. */
    public CustomerParcel view(Parcel p) {
        boolean outForDelivery = p.getActiveAssignmentId() != null && !p.isDelivered();
        String officeId = p.getOfficeId() != null ? p.getOfficeId() : p.getToOfficeId();
        Office office = officeId == null ? null : officeRepository.findById(officeId).orElse(null);
        return CustomerParcel.builder()
                .parcelId(p.getParcelId()).parcelDescription(p.getParcelDescription()).typeofParcel(p.getTypeofParcel())
                .numberOfItems(p.getNumberOfItems()).parcelWeight(p.getParcelWeight()).fragile(p.isFragile())
                .senderName(p.getSenderName()).receiverName(p.getReceiverName()).receiverAddress(p.getReceiverAddress())
                .receiverLatitude(p.getReceiverLatitude()).receiverLongitude(p.getReceiverLongitude())
                .receiverLocationName(p.getReceiverLocationName()).receiverPlaceId(p.getReceiverPlaceId())
                .receiverLocationSource(p.getReceiverLocationSource())
                .shelfName(p.getShelfName()).hasArrivedAtOffice(p.isHasArrivedAtOffice() || (!p.isParcelTransfer() && p.getOfficeId() != null))
                .parcelAssigned(p.isParcelAssigned()).homeDelivery(p.isHomeDelivery()).delivered(p.isDelivered()).pickedUp(p.isPickedUp())
                .parcelStatus(p.getParcelStatus())
                .inboundCost(p.getInboundCost()).storageCost(p.getStorageCost()).deliveryCost(p.getDeliveryCost())
                .pickUpCost(p.getPickUpCost()).inboudPayed(p.isInboudPayed()).pod(p.isPOD()).itemCost(p.getItemCost())
                .deliveryPrepaidAmount(p.getDeliveryPrepaidAmount())
                .deliveryBooked(p.getCustomerDeliveryRequestId() != null)
                .scheduledDeliveryDate(p.getScheduledDeliveryDate()).scheduledDeliveryTime(p.getScheduledDeliveryTime())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .riderInfo(outForDelivery && p.getRiderInfo() != null
                        ? new CustomerViews.RiderView(p.getRiderInfo().getRiderName(), p.getRiderInfo().getRiderPhoneNumber()) : null)
                .office(officeView(office))
                .build();
    }

    public static OfficeView officeView(Office o) {
        if (o == null) return null;
        return OfficeView.builder().id(o.getId()).name(o.getName()).address(o.getAddress()).phoneNumber(o.getPhoneNumber())
                .latitude(o.getLatitude()).longitude(o.getLongitude()).openingHours(o.getOpeningHours()).build();
    }

    // ================================================================== one-time codes

    public Map<String, String> sendOtp(String phone) {
        if (parcelsForPhone(phone, true).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No parcels found for this phone number");
        }
        String code = links.newOtp(phone);
        notification.send(NotificationRequestTemplate.builder().to(PhoneNumberUtils.normalizeGhana(phone)).otp(true)
                .body("Your parcel code is " + code + ". It expires in 5 minutes. Don't share it with anyone.").build());
        return Map.of("message", "Code sent");
    }

    public Map<String, String> verifyOtp(String phone, String code) {
        return Map.of("token", links.verifyOtp(phone, code));
    }

    // ================================================================== quote and booking

    public CustomerViews.QuoteResponse quote(CustomerViews.QuoteRequest req) {
        String phone = links.requirePhone(req.getToken(), req.getPhoneNumber());
        if (req.getLatitude() == null || req.getLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pin the delivery location on the map");
        }
        locationValidator.validateDropoff(req.getLatitude(), req.getLongitude());
        List<Parcel> parcels = bookableParcels(phone, req.getParcelIds());
        String officeId = stationOf(parcels.get(0));
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found"));
        DeliveryPricingTable table = pricing.currentTable(officeId);
        if (table == null || office.getLatitude() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Online delivery booking isn't available at this station yet; please call the station");
        }

        List<Double> singlePrices = new ArrayList<>();
        DeliveryPricingService.Quote first = null;
        Map<String, Double> priceByParcel = new HashMap<>();
        for (Parcel p : parcels) {
            DeliveryPricingService.Quote q = pricing.quote(office, table, req.getLatitude(), req.getLongitude(),
                    p.getParcelWeight() > 0 ? p.getParcelWeight() : null, p.isFragile());
            if (first == null) first = q;
            singlePrices.add(q.getPrice());
            priceByParcel.put(p.getParcelId(), q.getPrice());
        }
        double deliveryFee = DeliveryPricingRules.tripPrice(singlePrices, table.getExtraParcelFee());

        // Delivery fee shares: the dearest parcel carries its own price, each other one the extra fee.
        String dearest = parcels.stream().max((a, b) -> Double.compare(priceByParcel.get(a.getParcelId()), priceByParcel.get(b.getParcelId())))
                .map(Parcel::getParcelId).orElse(null);
        List<DeliveryQuote.ParcelShare> shares = new ArrayList<>();
        long chargesP = 0;
        for (Parcel p : parcels) {
            long owedP = owedCharges(p);
            chargesP += owedP;
            double fee = p.getParcelId().equals(dearest) ? priceByParcel.get(p.getParcelId()) : table.getExtraParcelFee();
            shares.add(new DeliveryQuote.ParcelShare(p.getParcelId(), fee, Money.c(owedP)));
        }
        double charges = Money.c(chargesP);
        double total = Money.c(Money.p(deliveryFee) + chargesP);
        long now = System.currentTimeMillis();
        DeliveryQuote saved = mongoTemplate.insert(DeliveryQuote.builder()
                .kind("CUSTOMER").companyId(office.getCompanyId()).officeId(officeId).phoneNumber(phone)
                .parcelIds(parcels.stream().map(Parcel::getParcelId).toList())
                .latitude(req.getLatitude()).longitude(req.getLongitude())
                .distanceKm(first.getDistanceKm()).distanceSource(first.getDistanceSource()).zone(first.getZone())
                .bucketKey(first.getBucketKey()).bucketLabel(first.getBucketLabel()).tableVersion(first.getTableVersion())
                .lines(first.getLines()).deliveryFee(deliveryFee).parcelCharges(charges).total(total).shares(shares)
                .createdAt(now).validUntil(now + QUOTE_VALID_MS).expiresAt(new Date(now + QUOTE_KEEP_MS))
                .build());
        return CustomerViews.QuoteResponse.builder().deliveryFee(deliveryFee).parcelCharges(charges).total(total)
                .quoteId(saved.getId()).distanceKm(first.getDistanceKm()).validUntil(saved.getValidUntil()).build();
    }

    public Map<String, String> book(CustomerViews.BookingRequest req) {
        String phone = links.requirePhone(req.getToken(), req.getPhoneNumber());
        if (paystackSecret.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Online payment isn't set up yet; please call the station");
        }
        if (req.getPaystackReference() == null || req.getPaystackReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paystackReference is required");
        }
        String reference = req.getPaystackReference().trim();
        if (mongoTemplate.exists(new Query(Criteria.where("paystackReference").is(reference)), CustomerDeliveryRequest.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment reference was already used");
        }
        DeliveryQuote quote = req.getQuoteId() == null ? null : mongoTemplate.findById(req.getQuoteId(), DeliveryQuote.class);
        if (quote == null || !"CUSTOMER".equals(quote.getKind()) || !Objects.equals(quote.getPhoneNumber(), phone)
                || !new HashSet<>(quote.getParcelIds()).equals(new HashSet<>(req.getParcelIds() == null ? List.of() : req.getParcelIds()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Get a new price for these parcels");
        }
        if (quote.getBookedRequestId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This quote was already booked");
        if (System.currentTimeMillis() > quote.getValidUntil()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The price has expired; get a new price before paying");
        }

        // Verify the payment before anything else.
        double paid = verifyPaystack(reference, quote.getTotal());

        CustomerDeliveryRequest request = CustomerDeliveryRequest.builder()
                .companyId(quote.getCompanyId()).officeId(quote.getOfficeId()).phoneNumber(phone)
                .parcelIds(quote.getParcelIds()).latitude(req.getLatitude() != null ? req.getLatitude() : quote.getLatitude())
                .longitude(req.getLongitude() != null ? req.getLongitude() : quote.getLongitude())
                .address(req.getAddress()).notes(req.getNotes())
                .scheduledDate(req.getScheduledDate()).scheduledTime(req.getScheduledTime())
                .quoteId(quote.getId()).deliveryFee(quote.getDeliveryFee()).parcelCharges(quote.getParcelCharges())
                .total(quote.getTotal()).amountPaid(req.getAmountPaid() != null ? req.getAmountPaid() : paid)
                .paystackReference(reference).paystackAmount(paid).createdAt(System.currentTimeMillis())
                .build();

        try {
            CustomerDeliveryRequest saved = transactionTemplate.execute(tx -> {
                // Same checks as the quote, now that the money is in.
                bookableParcels(phone, quote.getParcelIds());
                request.setStatus("PENDING");
                CustomerDeliveryRequest r = mongoTemplate.insert(request);
                for (DeliveryQuote.ParcelShare share : quote.getShares()) {
                    Update u = new Update()
                            .set("homeDelivery", true).set("hasCalled", true)
                            .set("receiverLatitude", r.getLatitude()).set("receiverLongitude", r.getLongitude())
                            .set("receiverLocationSource", LocationSource.MAP_PIN)
                            .set("deliveryCost", share.getDeliveryFee()).set("suggestedDeliveryCost", share.getDeliveryFee())
                            .set("distanceKm", quote.getDistanceKm()).set("distanceSource", quote.getDistanceSource())
                            .set("pricingTableVersion", quote.getTableVersion()).set("pricingZone", quote.getZone())
                            .set("pricingBucketKey", quote.getBucketKey())
                            .set("deliveryPrepaidAmount", Money.c(Money.p(share.getDeliveryFee()) + Money.p(share.getCharges())))
                            .set("deliveryPaymentReference", reference).set("customerDeliveryRequestId", r.getId())
                            .set("scheduledDeliveryDate", r.getScheduledDate()).set("scheduledDeliveryTime", r.getScheduledTime())
                            .set("callCenterRemark", "Customer booked delivery online (" + reference + ")");
                    if (r.getAddress() != null && !r.getAddress().isBlank()) u.set("receiverAddress", r.getAddress());
                    if (r.getNotes() != null && !r.getNotes().isBlank()) u.set("specialInstructions", r.getNotes());
                    long n = mongoTemplate.updateFirst(new Query(new Criteria().andOperator(
                            Criteria.where("_id").is(share.getParcelId()),
                            new Criteria().orOperator(Criteria.where("customerDeliveryRequestId").exists(false), Criteria.where("customerDeliveryRequestId").is(null)),
                            new Criteria().orOperator(Criteria.where("activeAssignmentId").exists(false), Criteria.where("activeAssignmentId").is(null)))),
                            u, Parcel.class).getModifiedCount();
                    if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel " + share.getParcelId() + " changed while you were paying");
                }
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(quote.getId())),
                        new Update().set("bookedRequestId", r.getId()), DeliveryQuote.class);
                return r;
            });
            return Map.of("requestId", saved.getId(), "status", saved.getStatus());
        } catch (ResponseStatusException | org.springframework.dao.DataAccessException e) {
            // Paid but not applied: keep the record for the station to refund or handle.
            String reason = e instanceof ResponseStatusException rse ? rse.getReason() : "Could not save the booking";
            request.setId(null);
            request.setStatus("FLAGGED");
            request.setFlagReason(reason);
            try {
                mongoTemplate.insert(request);
            } catch (DuplicateKeyException dup) {
                log.error("Delivery booking {} flagged twice", reference);
            }
            log.warn("Customer delivery booking {} flagged: {}", reference, reason);
            throw new ResponseStatusException(HttpStatus.CONFLICT, reason + ". Your payment (" + reference
                    + ") was received; please call the station with this reference.");
        }
    }

    /** Verifies with Paystack: success, in GHS, at least the quoted total. Returns the amount in cedis. */
    private double verifyPaystack(String reference, double total) {
        JsonNode data;
        try {
            String raw = webClient.get().uri("https://api.paystack.co/transaction/verify/{ref}", reference)
                    .header("Authorization", "Bearer " + paystackSecret)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));
            data = objectMapper.readTree(raw).path("data");
        } catch (Exception e) {
            log.warn("Paystack verify failed for {}: {}", reference, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "We couldn't confirm the payment yet; try again in a minute");
        }
        if (!"success".equals(data.path("status").asText())) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "The payment hasn't gone through");
        }
        if (!"GHS".equalsIgnoreCase(data.path("currency").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The payment must be in GHS");
        }
        long amountPesewas = data.path("amount").asLong();
        if (amountPesewas < Money.p(total)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The payment is less than the total of GHC " + Money.format(Money.p(total)));
        }
        return Money.c(amountPesewas);
    }

    // ================================================================== helpers

    /** The parcels named, checked: the phone's (403), not finished, not with a rider, not already booked (409), one station. */
    private List<Parcel> bookableParcels(String phone, List<String> parcelIds) {
        if (parcelIds == null || parcelIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one parcel");
        Set<String> variants = phoneVariants(phone);
        List<Parcel> out = new ArrayList<>();
        Set<String> stations = new HashSet<>();
        for (String id : new java.util.LinkedHashSet<>(parcelIds)) {
            Parcel p = mongoTemplate.findById(id, Parcel.class);
            if (p == null || !variants.contains(p.getRecieverPhoneNumber())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Parcel " + id + " isn't for this phone number");
            }
            if (p.isDelivered()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel " + id + " is already delivered");
            if (p.isPickedUp() || p.getCounterPayment() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel " + id + " was already collected");
            if (p.getParcelStatus() == ParcelStatus.REVERSED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel " + id + " was returned to the sender");
            if (p.getActiveAssignmentId() != null || p.isParcelAssigned()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel " + id + " is already out for delivery");
            if (p.getCustomerDeliveryRequestId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery for parcel " + id + " is already booked");
            stations.add(stationOf(p));
            out.add(p);
        }
        if (stations.size() > 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book parcels from one station at a time");
        if (stations.contains(null)) throw new ResponseStatusException(HttpStatus.CONFLICT, "We don't know which station will hold this parcel yet");
        return out;
    }

    private static String stationOf(Parcel p) {
        return p.getOfficeId() != null ? p.getOfficeId() : p.getToOfficeId();
    }

    /** What the customer still owes for a parcel: unpaid inbound, storage, pickup fee, POD item value. */
    private static long owedCharges(Parcel p) {
        long c = 0;
        if (!p.isInboudPayed()) c += Money.p(p.getInboundCost());
        c += Money.p(p.getStorageCost()) + Money.p(p.getPickUpCost());
        if (p.isPOD()) c += Money.p(p.getItemCost());
        return c;
    }

    /** The phone's parcels, newest first. onlyOpen drops delivered, collected and returned ones. */
    private List<Parcel> parcelsForPhone(String phone, boolean onlyOpen) {
        Set<String> variants = phoneVariants(phone);
        if (variants.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required");
        Criteria c = Criteria.where("recieverPhoneNumber").in(variants).and("isDelivered").is(false);
        if (onlyOpen) c = c.and("pickedUp").ne(true).and("parcelStatus").ne(ParcelStatus.REVERSED);
        return mongoTemplate.find(new Query(c).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(50), Parcel.class);
    }

    /** +233XXXXXXXXX, 0XXXXXXXXX and 233XXXXXXXXX: receiver numbers are stored in all three forms. */
    static Set<String> phoneVariants(String phone) {
        Set<String> out = new HashSet<>();
        String n = PhoneNumberUtils.normalizeGhana(phone);
        if (n == null) return out;
        out.add(n);
        if (phone != null) out.add(phone.trim());
        if (n.startsWith("+233") && n.length() == 13) {
            out.add("0" + n.substring(4));
            out.add(n.substring(1));
        }
        return out;
    }

    /** Arrival SMS to the receiver with the /receive link (off unless shortly.sms.receiver-arrival-enabled). */
    public void sendArrivalSms(Parcel p, boolean enabled) {
        if (!enabled || p.getRecieverPhoneNumber() == null || p.isDelivered()) return;
        try {
            Office office = stationOf(p) == null ? null : officeRepository.findById(stationOf(p)).orElse(null);
            String msg = "Hello " + (p.getReceiverName() != null ? p.getReceiverName() : "") + ", your parcel " + p.getParcelId()
                    + " has arrived" + (office != null ? " at " + office.getName() : "") + ".";
            notification.send(NotificationRequestTemplate.builder().to(p.getRecieverPhoneNumber())
                    .body(links.appendLink(msg, p)).build());
        } catch (RuntimeException e) {
            log.warn("Arrival SMS for {} failed: {}", p.getParcelId(), e.getMessage());
        }
    }
}
