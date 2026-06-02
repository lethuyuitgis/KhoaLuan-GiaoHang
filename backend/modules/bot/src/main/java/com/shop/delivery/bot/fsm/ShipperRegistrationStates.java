package com.shop.delivery.bot.fsm;

/**
 * String constants for the shipper-registration FSM. The underlying
 * {@link ConversationState} entity stores state as a free-form string, so we
 * keep this as a tiny holder class — same pattern as {@code CUSTOMER_RATING_COMMENT}
 * embedded in {@code RatingCommentHandler}.
 *
 * <p>Flow:
 * <pre>
 *   /start → ROLE:SHIPPER callback → AWAITING_NAME
 *                                  → AWAITING_PHONE (request_contact)
 *                                  → AWAITING_VEHICLE (inline keyboard)
 *                                  → AWAITING_PLATE
 *                                  → (clear) + INSERT user_role + shipper_profile
 * </pre>
 *
 * <p>Payload keys stored on each {@code ConversationState.data}:
 * <ul>
 *   <li>{@code fullName} (String)</li>
 *   <li>{@code phone} (String, normalised)</li>
 *   <li>{@code vehicleType} (String — MOTORBIKE/CAR/BICYCLE)</li>
 * </ul>
 */
public final class ShipperRegistrationStates {

    public static final String AWAITING_NAME    = "SHIPPER_REG_AWAITING_NAME";
    public static final String AWAITING_PHONE   = "SHIPPER_REG_AWAITING_PHONE";
    public static final String AWAITING_VEHICLE = "SHIPPER_REG_AWAITING_VEHICLE";
    public static final String AWAITING_PLATE   = "SHIPPER_REG_AWAITING_PLATE";

    public static final String KEY_FULL_NAME    = "fullName";
    public static final String KEY_PHONE        = "phone";
    public static final String KEY_VEHICLE_TYPE = "vehicleType";

    private ShipperRegistrationStates() {}

    public static boolean isShipperRegistration(String state) {
        return state != null && state.startsWith("SHIPPER_REG_");
    }
}
