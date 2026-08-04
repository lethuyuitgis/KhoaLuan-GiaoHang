package com.shop.delivery.bot.fsm;

/**
 * FSM constants for the anonymous customer↔shipper chat. A user in
 * {@link #CHAT_ACTIVE} has every plain-text message relayed to the other party
 * of the delivery until they {@code /thoat} (or the delivery finishes).
 *
 * <p>Payload on {@link ConversationState#getData()}:
 * <ul>
 *   <li>{@code assignmentId} — UUID string of the delivery being chatted about</li>
 *   <li>{@code role} — {@code CUSTOMER} / {@code SHIPPER}, the sender's side</li>
 * </ul>
 */
public final class ChatStates {

    public static final String CHAT_ACTIVE = "CHAT_ACTIVE";

    public static final String KEY_ASSIGNMENT_ID = "assignmentId";
    public static final String KEY_ROLE = "role";

    /** Callback data prefix that opens chat mode for an assignment. */
    public static final String CALLBACK_CHAT_OPEN = "CHAT_OPEN:";

    /** Command that leaves chat mode. */
    public static final String EXIT_COMMAND = "/thoat";

    private ChatStates() {}
}
