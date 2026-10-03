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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class VrmsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String adminToken;

    @BeforeEach
    void signInStaff() throws Exception {
        adminToken = login("staff@test.rw", "test-admin-pass");
    }

    // --- helpers -------------------------------------------------------------------------------

    String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    ResultActions send(String method, String url, String token, Object body) throws Exception {
        var req = request(org.springframework.http.HttpMethod.valueOf(method), url)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    String createVehicle(String plate, double rate) throws Exception {
        return read(send("POST", "/api/vehicles", adminToken,
                Map.of("plateNumber", plate, "model", "Toyota RAV4", "dailyRate", rate, "category", "SUV"))
                .andExpect(status().isCreated())).get("vehicleId").asText();
    }

    String registerCustomer(String email, String license) throws Exception {
        return read(send("POST", "/api/auth/register", null, Map.of(
                "fullName", "Aline Uwase", "email", email, "phoneNumber", "+250 788 245 610",
                "driverLicenseNumber", license, "password", "secret-pass-1"))
                .andExpect(status().isCreated())).get("token").asText();
    }

    // --- auth & access ---------------------------------------------------------------------------

    @Test
    void staffAccountIsSeededAndCanReadMe() throws Exception {
        send("GET", "/api/auth/me", adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.fullName").value("Grace Kamanzi"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        send("POST", "/api/auth/login", null, Map.of("email", "staff@test.rw", "password", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void fleetIsPublicButStaffEndpointsAreNot() throws Exception {
        send("GET", "/api/vehicles", null, null).andExpect(status().isOk());
        send("GET", "/api/customers", null, null).andExpect(status().isUnauthorized());

        String customerToken = registerCustomer("aline@email.com", "DL-48219");
        send("GET", "/api/customers", customerToken, null).andExpect(status().isForbidden());
        send("GET", "/api/dashboard", customerToken, null).andExpect(status().isForbidden());
    }

    // --- validation rules (BR-01, BR-02, BR-04) ---------------------------------------------------

    @Test
    void plateMustBeRwandanFormatAndIsNormalized() throws Exception {
        send("POST", "/api/vehicles", adminToken, Map.of("plateNumber", "XYZ 123", "model", "Car", "dailyRate", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.plateNumber", containsString("Rwandan format")));

        send("POST", "/api/vehicles", adminToken, Map.of("plateNumber", "rab 123 a", "model", "Car", "dailyRate", 10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateNumber").value("RAB123A"));

        send("POST", "/api/vehicles", adminToken, Map.of("plateNumber", "RAB123A", "model", "Car", "dailyRate", 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.plateNumber").exists());
    }

    @Test
    void driverLicenseMustStartWithDlAndDuplicatesAreRejected() throws Exception {
        Map<String, Object> bad = Map.of("fullName", "Eric N", "email", "eric@email.com", "driverLicenseNumber", "12345");
        send("POST", "/api/customers", adminToken, bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.driverLicenseNumber", containsString("DL-")));

        Map<String, Object> good = Map.of("fullName", "Eric N", "email", "eric@email.com", "driverLicenseNumber", "DL-19385");
        send("POST", "/api/customers", adminToken, good).andExpect(status().isCreated());
        send("POST", "/api/customers", adminToken, good)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void registeringLinksToExistingWalkInCustomer() throws Exception {
        send("POST", "/api/customers", adminToken,
                Map.of("fullName", "Aline U", "email", "aline@email.com", "driverLicenseNumber", "DL-48219"))
                .andExpect(status().isCreated());
        registerCustomer("aline@email.com", "DL-48219");

        send("GET", "/api/customers", adminToken, null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hasAccount").value(true));
    }

    // --- contract lifecycle ------------------------------------------------------------------------

    @Test
    void onlineBookingReservesVehicleThenStaffApproveAndReturn() throws Exception {
        String vehicleId = createVehicle("RAB123A", 85_000);
        String customerToken = registerCustomer("aline@email.com", "DL-48219");
        LocalDate start = LocalDate.now().plusDays(1);

        JsonNode booking = read(send("POST", "/api/me/bookings", customerToken, Map.of(
                "vehicleId", vehicleId, "startDate", start.toString(), "endDate", start.plusDays(5).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractStatus").value("PENDING"))
                .andExpect(jsonPath("$.totalCost").value(425_000.0)));
        String id = booking.get("contractId").asText();

        send("GET", "/api/vehicles/" + vehicleId, null, null).andExpect(jsonPath("$.vehicleStatus").value("RESERVED"));

        // The same vehicle can't be booked twice
        String other = registerCustomer("patrick@email.com", "DL-30184");
        send("POST", "/api/me/bookings", other, Map.of("vehicleId", vehicleId,
                "startDate", start.toString(), "endDate", start.plusDays(2).toString()))
                .andExpect(status().isConflict());

        send("PATCH", "/api/contracts/" + id + "/status", adminToken, Map.of("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedByName").value("Grace Kamanzi"));
        send("GET", "/api/vehicles/" + vehicleId, null, null).andExpect(jsonPath("$.vehicleStatus").value("RENTED"));

        // An active rental can't be cancelled by the customer online
        send("POST", "/api/me/bookings/" + id + "/cancel", customerToken, null).andExpect(status().isBadRequest());

        send("PATCH", "/api/contracts/" + id + "/status", adminToken, Map.of("status", "COMPLETED"))
                .andExpect(status().isOk());
        send("GET", "/api/vehicles/" + vehicleId, null, null).andExpect(jsonPath("$.vehicleStatus").value("AVAILABLE"));

        // Completed is final
        send("PATCH", "/api/contracts/" + id + "/status", adminToken, Map.of("status", "ACTIVE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffIssuedContractStartsActiveAndBlocksDeletes() throws Exception {
        String vehicleId = createVehicle("RAC902M", 95_000);
        String customerId = read(send("POST", "/api/customers", adminToken,
                Map.of("fullName", "Claire I", "email", "claire@email.com", "driverLicenseNumber", "DL-72014"))
                .andExpect(status().isCreated())).get("customerId").asText();

        LocalDate start = LocalDate.now();
        send("POST", "/api/contracts", adminToken, Map.of("customerId", customerId, "vehicleId", vehicleId,
                "startDate", start.toString(), "endDate", start.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.endDate").exists());

        send("POST", "/api/contracts", adminToken, Map.of("customerId", customerId, "vehicleId", vehicleId,
                "startDate", start.toString(), "endDate", start.plusDays(3).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.totalCost").value(285_000.0));

        // BR-06: can't delete while the rental is open
        send("DELETE", "/api/vehicles/" + vehicleId, adminToken, null).andExpect(status().isConflict());
        send("DELETE", "/api/customers/" + customerId, adminToken, null).andExpect(status().isConflict());

        send("GET", "/api/dashboard", adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeContracts").value(1))
                .andExpect(jsonPath("$.rentedVehicles").value(1))
                .andExpect(jsonPath("$.revenueLast30Days").value(285_000.0));

        send("GET", "/api/logs", adminToken, null)
                .andExpect(jsonPath("$[*].event", hasItem("New contract")));
    }

    @Test
    void unknownIdsReturn404() throws Exception {
        send("GET", "/api/vehicles/" + UUID.randomUUID(), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle not found"));
    }
}
