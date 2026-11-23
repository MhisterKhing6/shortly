package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;

/**
 * Body of POST /api-frontdesk/reconcilation-parcels: either a single reconciliation item or an
 * array of them.
 */
@Getter
public class ReconciliationBatchRequest {
    private final List<ReconcilationRiderRequest> items;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ReconciliationBatchRequest(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<ReconcilationRiderRequest> items) {
        this.items = items;
    }
}
