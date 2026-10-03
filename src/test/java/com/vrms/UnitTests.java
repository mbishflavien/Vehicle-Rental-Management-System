package com.vrms;

import com.vrms.messaging.RentalEvent;
import com.vrms.model.Permission;
import com.vrms.model.Role;
import com.vrms.model.Vehicle;
import com.vrms.notification.MessageTemplates;
import com.vrms.service.ContractService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fast unit tests for pure logic: no Spring context, no containers. */
class UnitTests {

    @Nested
    class Rbac {
        @Test
        void adminHasEveryStaffPermissionButCannotBookAsCustomer() {
            assertThat(Role.ADMIN.getPermissions()).contains(Permission.STAFF_MANAGE, Permission.AUDIT_READ, Permission.VEHICLE_DELETE)
                    .doesNotContain(Permission.BOOKING_OWN);
        }

        @Test
        void agentRunsRentalsButCannotDeleteOrManageStaff() {
            assertThat(Role.AGENT.getPermissions()).contains(Permission.CONTRACT_WRITE, Permission.CUSTOMER_WRITE)
                    .doesNotContain(Permission.VEHICLE_DELETE, Permission.CUSTOMER_DELETE, Permission.CONTRACT_DELETE,
                            Permission.AUDIT_READ, Permission.STAFF_MANAGE);
        }

        @Test
        void customerOnlyBooksForThemselves() {
            assertThat(Role.CUSTOMER.getPermissions()).containsExactly(Permission.BOOKING_OWN);
            assertThat(Role.CUSTOMER.isStaff()).isFalse();
            assertThat(Role.AGENT.authorities()).extracting(Object::toString).contains("ROLE_AGENT", "CONTRACT_WRITE");
        }
    }

    @Nested
    class Rules {
        @ParameterizedTest
        @CsvSource({"rab 123 a,RAB123A", "RAB123A,RAB123A", " rac  902 m ,RAC902M"})
        void platesAreNormalized(String input, String expected) {
            assertThat(Vehicle.normalizePlate(input)).isEqualTo(expected);
        }

        @Test
        void rentalDaysAreAtLeastOne() {
            LocalDate d = LocalDate.of(2026, 6, 12);
            assertThat(ContractService.rentalDays(d, d.plusDays(5))).isEqualTo(5);
            assertThat(ContractService.rentalDays(d, d)).isEqualTo(1);
        }
    }

    @Nested
    class Notifications {
        final MessageTemplates templates = new MessageTemplates("https://vrms.example");

        RentalEvent booking(String name) {
            LocalDate start = LocalDate.of(2026, 6, 12);
            return new RentalEvent(UUID.randomUUID(), RentalEvent.Type.BOOKING_REQUESTED, Instant.now(),
                    UUID.fromString("ab12cd34-0000-0000-0000-000000000000"), UUID.randomUUID(), name, "a@b.rw",
                    "+250788000000", "Toyota RAV4", "RAB123A", "Kigali Central", start, start.plusDays(5), 425_000.0, null);
        }

        @Test
        void emailHasTripDetailsAndEscapesCustomerInput() {
            MessageTemplates.Email email = templates.staffAlert(booking("<b>Eve</b>"));
            assertThat(email.subject()).isEqualTo("New booking request: <b>Eve</b> · Toyota RAV4");
            assertThat(email.html()).contains("CTR-AB12", "RWF 425,000", "5 days", "https://vrms.example/admin/contracts")
                    .contains("&lt;b&gt;Eve&lt;/b&gt;").doesNotContain("<b>Eve</b>");
        }

        @Test
        void smsIsShort() {
            String sms = templates.customerSms(booking("Aline Uwase"));
            assertThat(sms).startsWith("VRMS: booking received for Toyota RAV4, 12 Jun–17 Jun");
            assertThat(sms.length()).isLessThanOrEqualTo(160);
        }

        @ParameterizedTest
        @CsvSource({"+250 788 123 456,+250788123456", "0788123456,+250788123456", "250788123456,+250788123456"})
        void phoneNumbersBecomeInternational(String input, String expected) throws Exception {
            Method m = Class.forName("com.vrms.notification.NotificationListeners").getDeclaredMethod("toInternational", String.class);
            m.setAccessible(true);
            assertThat(m.invoke(null, input)).isEqualTo(expected);
        }
    }
}
