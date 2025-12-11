package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class ReconcilationRiderRequest {
    private String riderId;
    List<String> assignmentIds;
}
