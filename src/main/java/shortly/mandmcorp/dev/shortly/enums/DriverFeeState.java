package shortly.mandmcorp.dev.shortly.enums;

/** Driver fee line: WAITING -> READY -> PAID, or ON_HOLD (disputed) / NOT_PAYABLE (returned or cancelled). */
public enum DriverFeeState {
    WAITING,
    READY,
    PAID,
    ON_HOLD,
    NOT_PAYABLE
}
