package shortly.mandmcorp.dev.shortly.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable.Band;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable.WeightBand;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable.Zone;
import shortly.mandmcorp.dev.shortly.utils.GeoUtils;
import shortly.mandmcorp.dev.shortly.utils.Money;

/**
 * Delivery price from a station table (Part 5c). Mirrors the frontend's src/utils/deliveryPricing.ts:
 * 1. zone (smallest zone containing the pin) or distance (BANDS / PER_KM, km to one decimal);
 * 2. + the highest weight band surcharge with weight > overKg; 3. + fragileFee;
 * 4. round the total up to a multiple of roundTo.
 */
public final class DeliveryPricingRules {

    private DeliveryPricingRules() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private String label;
        private double amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Price {
        private double distanceKm;
        /** Zone name, when a zone priced it. */
        private String zone;
        /** Review bucket: "zone:<name>", "band:<upToKm>", "band:over" or "per_km". */
        private String bucketKey;
        private String bucketLabel;
        @Builder.Default
        private List<Line> lines = new ArrayList<>();
        private double price;
        private int tableVersion;
    }

    public static double roundKm(double km) {
        return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** @param pinLat,pinLng the drop-off (for zones); km the road/estimated distance. */
    public static Price price(DeliveryPricingTable t, double pinLat, double pinLng, double km, Double weightKg, boolean fragile) {
        double distance = roundKm(km);
        List<Line> lines = new ArrayList<>();
        Price.PriceBuilder out = Price.builder().distanceKm(distance).tableVersion(t.getVersion());

        // 1. zone or distance
        Zone zone = t.getZones() == null ? null : t.getZones().stream()
                .filter(z -> GeoUtils.haversineM(pinLat, pinLng, z.getLat(), z.getLng()) / 1000.0 <= z.getRadiusKm())
                .min(Comparator.comparingDouble(Zone::getRadiusKm)).orElse(null);
        long totalP;
        if (zone != null) {
            totalP = Money.p(zone.getPrice());
            lines.add(new Line("Zone: " + zone.getName(), Money.c(totalP)));
            out.zone(zone.getName()).bucketKey("zone:" + zone.getName()).bucketLabel(zone.getName());
        } else if ("PER_KM".equalsIgnoreCase(t.getMode())) {
            totalP = Math.max(Money.p(t.getMinFee()), Money.p(t.getBaseFee()) + Money.p(t.getPerKm() * distance));
            lines.add(new Line("Distance " + fmtKm(distance) + " km", Money.c(totalP)));
            out.bucketKey("per_km").bucketLabel("Per km");
        } else {
            List<Band> bands = sortedBands(t);
            Band match = null;
            double lower = 0;
            for (Band b : bands) {
                if (distance <= b.getUpToKm()) {
                    match = b;
                    break;
                }
                lower = b.getUpToKm();
            }
            if (match != null) {
                totalP = Money.p(match.getPrice());
                String label = fmtKm(lower) + "–" + fmtKm(match.getUpToKm()) + " km";
                lines.add(new Line("Distance " + label + " (" + fmtKm(distance) + " km)", Money.c(totalP)));
                out.bucketKey("band:" + fmtKm(match.getUpToKm())).bucketLabel(label);
            } else {
                Band last = bands.get(bands.size() - 1);
                long extraKm = (long) Math.ceil(distance - last.getUpToKm() - 1e-9);
                totalP = Money.p(last.getPrice()) + extraKm * Money.p(t.getPerKmBeyond());
                lines.add(new Line("Distance over " + fmtKm(last.getUpToKm()) + " km (" + fmtKm(distance) + " km)", Money.c(totalP)));
                out.bucketKey("band:over").bucketLabel("Over " + fmtKm(last.getUpToKm()) + " km");
            }
        }

        // 2. weight: the highest band with weight > overKg (bands don't add up)
        if (weightKg != null && t.getWeightBands() != null) {
            WeightBand wb = t.getWeightBands().stream().filter(w -> weightKg > w.getOverKg())
                    .max(Comparator.comparingDouble(WeightBand::getOverKg)).orElse(null);
            if (wb != null && wb.getSurcharge() > 0) {
                long s = Money.p(wb.getSurcharge());
                totalP += s;
                lines.add(new Line("Weight over " + fmtKm(wb.getOverKg()) + " kg", Money.c(s)));
            }
        }

        // 3. fragile
        if (fragile && t.getFragileFee() > 0) {
            long f = Money.p(t.getFragileFee());
            totalP += f;
            lines.add(new Line("Fragile", Money.c(f)));
        }

        // 4. round up to a multiple of roundTo
        long stepP = Money.p(t.getRoundTo());
        if (stepP > 0 && totalP % stepP != 0) {
            long rounded = ((totalP + stepP - 1) / stepP) * stepP;
            lines.add(new Line("Rounding", Money.c(rounded - totalP)));
            totalP = rounded;
        }
        return out.lines(lines).price(Money.c(totalP)).build();
    }

    /** Several parcels in one trip: the highest single price plus extraParcelFee for each extra parcel. */
    public static double tripPrice(List<Double> singlePrices, double extraParcelFee) {
        if (singlePrices.isEmpty()) return 0;
        long max = singlePrices.stream().mapToLong(Money::p).max().getAsLong();
        return Money.c(max + (singlePrices.size() - 1L) * Money.p(extraParcelFee));
    }

    /** 400 unless the table is usable. */
    public static void validate(DeliveryPricingTable t) {
        String mode = t.getMode() == null ? "BANDS" : t.getMode().trim().toUpperCase();
        if (!mode.equals("BANDS") && !mode.equals("PER_KM")) throw bad("mode must be BANDS or PER_KM");
        t.setMode(mode);
        for (double v : new double[] { t.getPerKmBeyond(), t.getBaseFee(), t.getPerKm(), t.getMinFee(), t.getFragileFee(), t.getRoundTo(), t.getExtraParcelFee() }) {
            if (v < 0 || Double.isNaN(v)) throw bad("Fees can't be negative");
        }
        if (mode.equals("BANDS")) {
            if (t.getBands() == null || t.getBands().isEmpty()) throw bad("Add at least one distance band");
            Set<Double> seen = new HashSet<>();
            for (Band b : t.getBands()) {
                if (b.getUpToKm() <= 0) throw bad("Band distances must be above 0 km");
                if (!seen.add(b.getUpToKm())) throw bad("Two bands have the same distance (" + fmtKm(b.getUpToKm()) + " km)");
                if (b.getPrice() <= 0) throw bad("Band prices must be above 0");
            }
            double prev = 0;
            for (Band b : sortedBands(t)) {
                if (b.getPrice() < prev) throw bad("The band up to " + fmtKm(b.getUpToKm()) + " km is cheaper than a shorter band");
                prev = b.getPrice();
            }
        } else if (t.getPerKm() <= 0 && t.getBaseFee() <= 0 && t.getMinFee() <= 0) {
            throw bad("Set a base fee, a per-km price or a minimum fee");
        }
        if (t.getWeightBands() != null) {
            for (WeightBand w : t.getWeightBands()) {
                if (w.getOverKg() < 0 || w.getSurcharge() < 0) throw bad("Weight bands can't be negative");
            }
        }
        if (t.getZones() != null) {
            Set<String> names = new HashSet<>();
            for (Zone z : t.getZones()) {
                if (z.getName() == null || z.getName().isBlank()) throw bad("Every zone needs a name");
                if (!names.add(z.getName().trim().toLowerCase())) throw bad("Two zones are called \"" + z.getName() + "\"");
                if (z.getRadiusKm() <= 0) throw bad("Zone \"" + z.getName() + "\" needs a radius above 0 km");
                if (z.getPrice() <= 0) throw bad("Zone \"" + z.getName() + "\" needs a price above 0");
                if (z.getLat() < -90 || z.getLat() > 90 || z.getLng() < -180 || z.getLng() > 180 || (z.getLat() == 0 && z.getLng() == 0)) {
                    throw bad("Zone \"" + z.getName() + "\" needs a valid centre");
                }
                z.setName(z.getName().trim());
            }
        }
    }

    static List<Band> sortedBands(DeliveryPricingTable t) {
        List<Band> bands = new ArrayList<>(t.getBands() == null ? List.of() : t.getBands());
        bands.sort(Comparator.comparingDouble(Band::getUpToKm));
        return bands;
    }

    static String fmtKm(double km) {
        return km == Math.rint(km) ? String.valueOf((long) km) : String.valueOf(roundKm(km));
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }
}
