package com.vrms.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Picks the SMS provider: Africa's Talking (widely used in Rwanda) when an API key is configured,
 * otherwise a simulated gateway that only records the message, so the app works without an account.
 */
@Configuration
public class SmsGateways {

    private static final Logger log = LoggerFactory.getLogger(SmsGateways.class);

    @Bean
    public SmsGateway smsGateway(@Value("${vrms.sms.africastalking.username:sandbox}") String username,
                                 @Value("${vrms.sms.africastalking.api-key:}") String apiKey,
                                 @Value("${vrms.sms.africastalking.sender-id:}") String senderId) {
        if (apiKey.isBlank()) {
            log.info("SMS: no Africa's Talking API key configured, messages are simulated");
            return new Simulated();
        }
        log.info("SMS: sending through Africa's Talking ({})", username);
        return new AfricasTalking(username, apiKey, senderId);
    }

    static final class Simulated implements SmsGateway {
        @Override
        public boolean send(String phoneNumber, String message) {
            log.info("[simulated SMS] to {}: {}", phoneNumber, message);
            return false;
        }

        @Override
        public String name() { return "Simulated"; }
    }

    /** https://developers.africastalking.com/docs/sms/sending */
    static final class AfricasTalking implements SmsGateway {
        private final String username;
        private final String senderId;
        private final RestClient client;

        AfricasTalking(String username, String apiKey, String senderId) {
            this.username = username;
            this.senderId = senderId;
            String host = "sandbox".equals(username) ? "https://api.sandbox.africastalking.com" : "https://api.africastalking.com";
            this.client = RestClient.builder().baseUrl(host)
                    .defaultHeader("apiKey", apiKey)
                    .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }

        @Override
        public boolean send(String phoneNumber, String message) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", username);
            form.add("to", phoneNumber);
            form.add("message", message);
            if (!senderId.isBlank()) form.add("from", senderId);
            JsonNode response = client.post().uri("/version1/messaging")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode recipient = response == null ? null : response.path("SMSMessageData").path("Recipients").path(0);
            String status = recipient == null ? "" : recipient.path("status").asText();
            if (!"Success".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Africa's Talking rejected the message: "
                        + (response == null ? "no response" : response.path("SMSMessageData").path("Message").asText(status)));
            }
            return true;
        }

        @Override
        public String name() { return "Africa's Talking"; }
    }
}
