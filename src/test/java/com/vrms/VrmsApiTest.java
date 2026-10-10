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
class VrmsApiTest extends IntegrationTest {

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

    // --- RBAC & security ------------------------------------------------------------------------

    @Test
    void agentCanRunRentalsButNotDeleteOrReadAuditLog() throws Exception {
        String agent = login("agent@test.rw", "test-agent-pass1");
        send("GET", "/api/auth/me", agent, null)
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.permissions", hasItem("CONTRACT_WRITE")))
                .andExpect(jsonPath("$.permissions", not(hasItem("VEHICLE_DELETE"))));

        String vehicleId = read(send("POST", "/api/vehicles", agent,
                Map.of("plateNumber", "RAB321C", "model", "Toyota Vitz", "dailyRate", 45_000))
                .andExpect(status().isCreated())).get("vehicleId").asText();
        send("GET", "/api/dashboard", agent, null).andExpect(status().isOk());

        send("DELETE", "/api/vehicles/" + vehicleId, agent, null).andExpect(status().isForbidden());
        send("GET", "/api/logs", agent, null).andExpect(status().isForbidden());
        send("GET", "/api/staff", agent, null).andExpect(status().isForbidden());

        send("DELETE", "/api/vehicles/" + vehicleId, adminToken, null).andExpect(status().isNoContent());
    }

    @Test
    void adminManagesStaffAndDisablingRevokesAccessImmediately() throws Exception {
        String id = read(send("POST", "/api/staff", adminToken, Map.of("fullName", "Diane Mukamana",
                "email", "diane@vrms.rw", "jobTitle", "Agent", "role", "AGENT", "password", "temp-pass-123"))
                .andExpect(status().isCreated())).get("userId").asText();

        String diane = login("diane@vrms.rw", "temp-pass-123");
        send("GET", "/api/contracts", diane, null).andExpect(status().isOk());

        send("PUT", "/api/staff/" + id, adminToken, Map.of("fullName", "Diane Mukamana",
                "email", "diane@vrms.rw", "role", "AGENT", "enabled", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Her still-unexpired token stops working, and she can't sign in again
        send("GET", "/api/contracts", diane, null).andExpect(status().isUnauthorized());
        send("POST", "/api/auth/login", null, Map.of("email", "diane@vrms.rw", "password", "temp-pass-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastAdminCannotBeDisabledOrDemoted() throws Exception {
        String adminId = read(send("GET", "/api/auth/me", adminToken, null)).get("userId").asText();
        send("PUT", "/api/staff/" + adminId, adminToken, Map.of("fullName", "Grace Kamanzi",
                "email", "staff@test.rw", "role", "AGENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailedSignInsAreThrottled() throws Exception {
        Map<String, String> wrong = Map.of("email", "staff@test.rw", "password", "wrong-password");
        for (int i = 0; i < 3; i++) {
            send("POST", "/api/auth/login", null, wrong).andExpect(status().isUnauthorized());
        }
        send("POST", "/api/auth/login", null, wrong).andExpect(status().isTooManyRequests());
        // Even the right password is refused during the lockout
        send("POST", "/api/auth/login", null, Map.of("email", "staff@test.rw", "password", "test-admin-pass"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void tamperedOrForeignTokensAreRejected() throws Exception {
        String[] parts = adminToken.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        send("GET", "/api/auth/me", tampered, null).andExpect(status().isUnauthorized());
        send("GET", "/api/auth/me", "not-a-jwt", null).andExpect(status().isUnauthorized());
    }

    @Test
    void customersCanChangeTheirPassword() throws Exception {
        String token = registerCustomer("aline@email.com", "DL-48219");
        send("PUT", "/api/auth/password", token, Map.of("currentPassword", "wrong", "newPassword", "new-pass-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
        send("PUT", "/api/auth/password", token, Map.of("currentPassword", "secret-pass-1", "newPassword", "new-pass-123"))
                .andExpect(status().isNoContent());
        login("aline@email.com", "new-pass-123");
    }

    // --- OAuth2 sign-in (Google / GitHub) -------------------------------------------------------

    @Autowired com.vrms.security.OAuthAccountService oauthAccounts;
    @Autowired com.vrms.security.JwtService jwtService;

    @Test
    void oauthSignInCreatesCustomerWhoMustCompleteProfileBeforeBooking() throws Exception {
        send("GET", "/api/auth/providers", null, null).andExpect(status().isOk());

        // A walk-in customer already exists with this email and license
        send("POST", "/api/customers", adminToken,
                Map.of("fullName", "Aline U", "email", "aline@gmail.com", "driverLicenseNumber", "DL-48219"))
                .andExpect(status().isCreated());

        var user = oauthAccounts.findOrCreate(com.vrms.model.AuthProvider.GOOGLE, "Aline@Gmail.com", "Aline Uwase");
        String token = jwtService.issueToken(user);
        send("GET", "/api/auth/me", token, null)
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
        send("GET", "/api/me/bookings", token, null).andExpect(status().isForbidden());

        // Wrong license for the walk-in profile is refused; the right one links it (no duplicate)
        send("PUT", "/api/me/profile", token, Map.of("driverLicenseNumber", "DL-99999"))
                .andExpect(status().isConflict());
        send("PUT", "/api/me/profile", token, Map.of("phoneNumber", "+250 788 245 610", "driverLicenseNumber", "dl-48219"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccount").value(true));
        send("GET", "/api/customers", adminToken, null).andExpect(jsonPath("$", hasSize(1)));
        send("GET", "/api/me/bookings", token, null).andExpect(status().isOk());

        // Signing in again with the same verified email reuses the account
        var again = oauthAccounts.findOrCreate(com.vrms.model.AuthProvider.GOOGLE, "aline@gmail.com", "Aline Uwase");
        org.junit.jupiter.api.Assertions.assertEquals(user.getUserId(), again.getUserId());
    }

    // --- MongoDB: audit trail and customer documents (GridFS) ----------------------------------------

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    ResultActions upload(String token, String type, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/me/documents")
                .file(new org.springframework.mock.web.MockMultipartFile("file", name, "image/png", bytes))
                .param("type", type)
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void customerUploadsLicenseScanAndStaffVerifiesIt() throws Exception {
        String customer = registerCustomer("aline@email.com", "DL-48219");
        String customerId = read(send("GET", "/api/auth/me", customer, null)).get("customerId").asText();

        // Content is checked by its bytes, not by the name or declared type
        upload(customer, "DRIVER_LICENSE", "license.png", "<script>alert(1)</script>".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.file").value("Upload a PDF, JPEG or PNG file"));

        String docId = read(upload(customer, "DRIVER_LICENSE", "license.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.fileId").doesNotExist())).get("documentId").asText();

        mvc.perform(get("/api/documents/" + docId + "/content").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        // Another customer can't see it
        String other = registerCustomer("eric@email.com", "DL-19385");
        send("GET", "/api/me/documents/" + docId + "/content", other, null).andExpect(status().isNotFound());

        send("PATCH", "/api/documents/" + docId + "/review", adminToken, Map.of("status", "VERIFIED"))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewedBy").value("Grace Kamanzi"));
        send("GET", "/api/customers/" + customerId + "/documents", adminToken, null)
                .andExpect(jsonPath("$", hasSize(1)));
        send("DELETE", "/api/me/documents/" + docId, customer, null).andExpect(status().isBadRequest());

        // Every step was written to the MongoDB audit trail
        send("GET", "/api/logs", adminToken, null)
                .andExpect(jsonPath("$[*].event", hasItems("Document uploaded", "Document reviewed")));
    }

    @Test
    void unknownIdsReturn404() throws Exception {
        send("GET", "/api/vehicles/" + UUID.randomUUID(), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Vehicle not found"));
    }
}
