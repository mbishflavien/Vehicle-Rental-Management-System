package com.vrms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end through RabbitMQ: an API action publishes an event, the consumers send real email to
 * the Mailpit SMTP inbox and record email/SMS notifications in MongoDB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class MessagingTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    final RestClient mailpit = RestClient.create();
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        mailpit.delete().uri(mailpitApi() + "/messages").retrieve().toBodilessEntity();
        admin = token("/api/auth/login", Map.of("email", "staff@test.rw", "password", "test-admin-pass"));
    }

    String token(String url, Map<String, ?> body) throws Exception {
        String res = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    JsonNode call(String method, String url, String token, Object body) throws Exception {
        var req = request(org.springframework.http.HttpMethod.valueOf(method), url)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(json.writeValueAsString(body));
        String res = mvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return res.isEmpty() ? null : json.readTree(res);
    }

    /** "to → subject" for every email in the inbox. */
    List<String> inbox() {
        JsonNode messages = mailpit.get().uri(mailpitApi() + "/messages").retrieve().body(JsonNode.class).path("messages");
        List<String> out = new ArrayList<>();
        messages.forEach(m -> out.add(m.path("To").path(0).path("Address").asText() + " → " + m.path("Subject").asText()));
        return out;
    }

    @Test
    void bookingAndApprovalSendEmailsAndTextsThroughRabbitMq() throws Exception {
        String vehicleId = call("POST", "/api/vehicles", admin,
                Map.of("plateNumber", "RAE440K", "model", "Toyota Prado", "dailyRate", 145_000)).get("vehicleId").asText();
        String customer = token("/api/auth/register", Map.of("fullName", "Aline Uwase", "email", "aline@email.com",
                "phoneNumber", "0788 245 610", "driverLicenseNumber", "DL-48219", "password", "secret-pass-1"));

        LocalDate start = LocalDate.now().plusDays(1);
        String contractId = call("POST", "/api/me/bookings", customer, Map.of("vehicleId", vehicleId,
                "startDate", start.toString(), "endDate", start.plusDays(3).toString())).get("contractId").asText();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(inbox()).contains(
                "aline@email.com → Welcome to VRMS Mobility",
                "aline@email.com → We've received your booking for the Toyota Prado",
                "operations@vrms.rw → New booking request: Aline Uwase · Toyota Prado"));

        call("PATCH", "/api/contracts/" + contractId + "/status", admin, Map.of("status", "ACTIVE"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(inbox())
                .contains("aline@email.com → Confirmed: your Toyota Prado is ready for pickup"));

        // Each delivery is recorded in MongoDB; SMS go to the normalized Rwandan number (simulated in tests)
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode sent = call("GET", "/api/notifications", admin, null);
            List<String> sms = new ArrayList<>();
            sent.forEach(n -> { if ("SMS".equals(n.path("channel").asText())) sms.add(n.path("recipient").asText() + " " + n.path("status").asText() + " " + n.path("eventType").asText()); });
            assertThat(sms).contains("+250788245610 SIMULATED booking.requested", "+250788245610 SIMULATED contract.approved");
        });

        // The customer sees their own messages, not anyone else's
        JsonNode mine = call("GET", "/api/me/notifications", customer, null);
        assertThat(mine.size()).isGreaterThanOrEqualTo(4);
        mine.forEach(n -> assertThat(n.path("recipient").asText()).isIn("aline@email.com", "+250788245610"));
    }

    @Test
    void failedActionsPublishNothing() throws Exception {
        String customer = token("/api/auth/register", Map.of("fullName", "Eric N", "email", "eric@email.com",
                "driverLicenseNumber", "DL-19385", "password", "secret-pass-1"));
        await().atMost(Duration.ofSeconds(20)).until(() -> inbox().contains("eric@email.com → Welcome to VRMS Mobility"));

        // Booking a vehicle that doesn't exist rolls back: no booking email may go out
        mvc.perform(post("/api/me/bookings").header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("vehicleId", java.util.UUID.randomUUID(),
                                "startDate", LocalDate.now().plusDays(1).toString(), "endDate", LocalDate.now().plusDays(2).toString()))))
                .andExpect(status().isNotFound());
        Thread.sleep(2000);
        assertThat(inbox()).noneMatch(m -> m.contains("booking"));
    }
}
