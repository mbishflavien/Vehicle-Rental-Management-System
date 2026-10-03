package com.vrms.notification;

/** Sends a text message. Implementations: Africa's Talking (real) or simulated. */
public interface SmsGateway {

    /** @return true if the provider accepted it, false if it was only simulated. */
    boolean send(String phoneNumber, String message);

    String name();
}
