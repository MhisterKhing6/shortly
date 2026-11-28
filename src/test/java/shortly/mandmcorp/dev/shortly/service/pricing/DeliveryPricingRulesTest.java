package shortly.mandmcorp.dev.shortly.service.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable.Band;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable.WeightBand;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable.Zone;

class DeliveryPricingRulesTest {

    private static DeliveryPricingTable bands() {
        return DeliveryPricingTable.builder().version(4).mode("BANDS")
                .bands(List.of(new Band(3, 15), new Band(6, 20), new Band(10, 25), new Band(15, 35)))
                .perKmBeyond(2)
                .weightBands(List.of(new WeightBand(5, 5), new WeightBand(10, 10)))
                .zones(List.of(new Zone("Airport Residential", 5.6055, -0.1780, 1.5, 18), new Zone("Big", 5.6055, -0.1780, 5, 30)))
                .fragileFee(3).roundTo(1).build();
    }

    private static final double FAR_LAT = 5.9, FAR_LNG = -0.5; // outside every zone

    @Test
    void bandPriceWeightAndRounding() {
        DeliveryPricingRules.Price p = DeliveryPricingRules.price(bands(), FAR_LAT, FAR_LNG, 13.46, 12.0, false);
        assertEquals(13.5, p.getDistanceKm());
        assertEquals(45, p.getPrice());                 // 35 band + 10 (highest weight band only)
        assertEquals("Distance 10–15 km (13.5 km)", p.getLines().get(0).getLabel());
        assertEquals("band:15", p.getBucketKey());
        assertEquals(4, p.getTableVersion());
        assertEquals(15, DeliveryPricingRules.price(bands(), FAR_LAT, FAR_LNG, 3.0, null, false).getPrice()); // km <= upToKm
        assertEquals(15, DeliveryPricingRules.price(bands(), FAR_LAT, FAR_LNG, 3.04, null, false).getPrice()); // rounds to 3.0 -> first band
        assertEquals(20, DeliveryPricingRules.price(bands(), FAR_LAT, FAR_LNG, 3.05, null, false).getPrice()); // rounds to 3.1 -> second band
    }

    @Test
    void pastTheLastBandAddsPerWholeExtraKm() {
        DeliveryPricingRules.Price p = DeliveryPricingRules.price(bands(), FAR_LAT, FAR_LNG, 17.2, null, false);
        assertEquals(35 + 3 * 2, p.getPrice()); // 2.2 extra km -> 3 whole km
        assertEquals("band:over", p.getBucketKey());
    }

    @Test
    void smallestZoneWinsOverDistance() {
        DeliveryPricingRules.Price p = DeliveryPricingRules.price(bands(), 5.6060, -0.1785, 13.0, null, true);
        assertEquals("Airport Residential", p.getZone());
        assertEquals(18 + 3, p.getPrice()); // zone + fragile
        assertEquals("Zone: Airport Residential", p.getLines().get(0).getLabel());
    }

    @Test
    void perKmModeAndRoundingUp() {
        DeliveryPricingTable t = DeliveryPricingTable.builder().mode("PER_KM").baseFee(10).perKm(2).minFee(15).roundTo(5).build();
        assertEquals(15, DeliveryPricingRules.price(t, FAR_LAT, FAR_LNG, 1.0, null, false).getPrice());   // min fee
        assertEquals(40, DeliveryPricingRules.price(t, FAR_LAT, FAR_LNG, 13.6, null, false).getPrice());  // 37.2 -> up to 40
        assertEquals(0.0, DeliveryPricingRules.price(t, FAR_LAT, FAR_LNG, 10, null, false).getLines().stream()
                .filter(l -> l.getLabel().equals("Rounding")).count());                                   // 30 exactly: no rounding line
    }

    @Test
    void severalParcelsInOneTrip() {
        assertEquals(35 + 5 + 5, DeliveryPricingRules.tripPrice(List.of(20.0, 35.0, 15.0), 5));
    }

    @Test
    void validation() {
        assertDoesNotThrow(() -> DeliveryPricingRules.validate(bands()));
        DeliveryPricingTable cheaper = bands();
        cheaper.setBands(List.of(new Band(3, 15), new Band(6, 12)));
        assertThrows(ResponseStatusException.class, () -> DeliveryPricingRules.validate(cheaper));
        DeliveryPricingTable dup = bands();
        dup.setBands(List.of(new Band(3, 15), new Band(3, 20)));
        assertThrows(ResponseStatusException.class, () -> DeliveryPricingRules.validate(dup));
        DeliveryPricingTable empty = bands();
        empty.setBands(List.of());
        assertThrows(ResponseStatusException.class, () -> DeliveryPricingRules.validate(empty));
        DeliveryPricingTable badZone = bands();
        badZone.setZones(List.of(new Zone("A", 5.6, -0.17, 0, 10)));
        assertThrows(ResponseStatusException.class, () -> DeliveryPricingRules.validate(badZone));
        DeliveryPricingTable negative = bands();
        negative.setFragileFee(-1);
        assertThrows(ResponseStatusException.class, () -> DeliveryPricingRules.validate(negative));
    }
}
