package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import lombok.Data;

public final class VendorApplicationRequests {

    private VendorApplicationRequests() {}

    @Data
    public static class Apply {
        private String businessName;
        private String contactName;
        private String phoneNumber;
        private String email;
        private String area;
        private Integer parcelsPerWeek;
        private String message;
    }

    @Data
    public static class Review {
        /** CONTACTED, APPROVED or REJECTED. */
        private String status;
        private String note;
        /** With APPROVED: also create the Vendor user for this phone number. */
        private boolean createVendor;
        /** Station(s) the new vendor works with; optional for vendors. */
        private List<String> officeIds;
    }

    @Data
    public static class Batch {
        private List<VendorParcelRequest> parcels;
    }
}
