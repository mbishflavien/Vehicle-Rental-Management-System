package com.vrms.notification;

import com.vrms.messaging.RentalEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** The words customers and staff receive for each event. */
@Component
public class MessageTemplates {

    public record Email(String subject, String html) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final String appUrl;

    public MessageTemplates(@Value("${vrms.app.base-url:http://localhost:8080}") String appUrl) {
        this.appUrl = appUrl;
    }

    public Email customerEmail(RentalEvent e) {
        String first = firstName(e.customerName());
        return switch (e.type()) {
            case BOOKING_REQUESTED -> email("We've received your booking for the " + e.vehicleModel(),
                    "Thanks, " + first + "! Your booking request is in.",
                    "We're holding the <b>" + esc(e.vehicleModel()) + "</b> for you while our team confirms it. You'll hear from us shortly.",
                    e, "View my bookings", "/account/bookings");
            case CONTRACT_ISSUED -> email("Your VRMS rental contract " + ref(e),
                    "Your keys are ready, " + first + ".",
                    "Your rental contract has been issued. Drive safely, and call +250 788 220 440 any time for roadside care.",
                    e, null, null);
            case CONTRACT_APPROVED -> email("Confirmed: your " + e.vehicleModel() + " is ready for pickup",
                    "You're confirmed, " + first + ".",
                    "Our team approved your booking. Bring your driver license to the pickup desk.",
                    e, "View my bookings", "/account/bookings");
            case CONTRACT_COMPLETED -> email("Thanks for travelling with VRMS",
                    "Welcome back, " + first + ".",
                    "Your " + esc(e.vehicleModel()) + " has been returned and your rental is complete. We hope to see you on the road again soon.",
                    e, "Book your next trip", "/fleet");
            case CONTRACT_CANCELLED -> email("Your booking " + ref(e) + " was cancelled",
                    "Your booking was cancelled.",
                    "The reservation below is cancelled and the vehicle released. If this wasn't expected, reply to this email or call +250 788 220 440.",
                    e, "Browse the fleet", "/fleet");
            case CUSTOMER_REGISTERED -> email("Welcome to VRMS Mobility",
                    "Welcome, " + first + ".",
                    "Your account is ready. Browse the fleet, reserve in a few clicks and manage your bookings online.",
                    null, "Browse the fleet", "/fleet");
            case DOCUMENT_VERIFIED -> email("Your document was verified",
                    "You're verified, " + first + ".",
                    "We've checked your " + esc(e.detail()) + ". Pickup will be quicker next time.",
                    null, null, null);
            case DOCUMENT_REJECTED -> email("We couldn't verify your document",
                    "Please upload your document again.",
                    "We couldn't verify your " + esc(e.detail()) + ". Please upload a clear photo or scan from your profile page.",
                    null, "Open my profile", "/account/profile");
        };
    }

    public String customerSms(RentalEvent e) {
        return switch (e.type()) {
            case BOOKING_REQUESTED -> "VRMS: booking received for " + e.vehicleModel() + ", " + shortDates(e) + ". We'll confirm shortly.";
            case CONTRACT_ISSUED -> "VRMS: contract " + ref(e) + " issued for " + e.vehicleModel() + " (" + e.plateNumber() + "). Total " + money(e.totalCost()) + ". Drive safe!";
            case CONTRACT_APPROVED -> "VRMS: confirmed! Pick up your " + e.vehicleModel() + " on " + DATE.format(e.startDate())
                    + (e.pickupBranch() == null ? " (we'll confirm the branch)." : " at " + e.pickupBranch() + ".");
            case CONTRACT_COMPLETED -> "VRMS: thanks for returning the " + e.vehicleModel() + ". See you on the road again!";
            case CONTRACT_CANCELLED -> "VRMS: booking " + ref(e) + " for " + e.vehicleModel() + " was cancelled. Questions? +250 788 220 440";
            case DOCUMENT_VERIFIED -> "VRMS: your " + e.detail() + " has been verified.";
            case DOCUMENT_REJECTED -> "VRMS: we couldn't verify your " + e.detail() + ". Please upload it again from your profile.";
            case CUSTOMER_REGISTERED -> null;
        };
    }

    public Email staffAlert(RentalEvent e) {
        return email("New booking request: " + e.customerName() + " · " + e.vehicleModel(),
                "A booking is waiting for approval.",
                esc(e.customerName()) + " (" + esc(e.customerEmail()) + ") requested the <b>" + esc(e.vehicleModel()) + "</b> online.",
                e, "Review in the staff console", "/admin/contracts?tab=Pending");
    }

    private Email email(String subject, String heading, String intro, RentalEvent trip, String cta, String path) {
        StringBuilder html = new StringBuilder()
                .append("<div style=\"background:#faf6f0;padding:32px 16px;font-family:Arial,Helvetica,sans-serif;color:#332b24\">")
                .append("<div style=\"max-width:560px;margin:auto;background:#fff;border:1px solid #e7ded2;border-radius:16px;overflow:hidden\">")
                .append("<div style=\"padding:22px 28px;border-bottom:1px solid #e7ded2;font-family:Georgia,serif;font-size:20px\">")
                .append("<span style=\"display:inline-block;width:30px;height:30px;line-height:30px;text-align:center;border-radius:50%;background:#c1622d;color:#fff;font-style:italic\">V</span>")
                .append(" VRMS <i style=\"color:#7a6f63;font-size:15px\">Mobility</i></div>")
                .append("<div style=\"padding:28px\"><h1 style=\"font-family:Georgia,serif;font-weight:400;font-size:28px;margin:0 0 12px\">")
                .append(esc(heading)).append("</h1><p style=\"color:#7a6f63;line-height:1.6;margin:0 0 20px\">").append(intro).append("</p>");
        if (trip != null && trip.vehicleModel() != null) {
            html.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px;background:#f7f2eb;border-radius:12px\">")
                .append(row("Booking", ref(trip)))
                .append(row("Vehicle", trip.vehicleModel() + " · " + trip.plateNumber()))
                .append(row("Dates", DATE.format(trip.startDate()) + " → " + DATE.format(trip.endDate())
                        + " (" + ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + " days)"))
                .append(row("Pickup", orTbc(trip.pickupBranch())))
                .append(row("Total", money(trip.totalCost())))
                .append("</table>");
        }
        if (cta != null) {
            html.append("<p style=\"margin:26px 0 0\"><a href=\"").append(appUrl).append(path)
                .append("\" style=\"background:#c1622d;color:#fff;text-decoration:none;padding:13px 22px;border-radius:999px;font-weight:bold;font-size:14px\">")
                .append(esc(cta)).append("</a></p>");
        }
        html.append("</div><div style=\"padding:18px 28px;background:#3a2e26;color:#d9cec3;font-size:12px\">")
            .append("VRMS Mobility · KN 5 Rd, Kigali · +250 788 220 440 · hello@vrms.rw</div></div></div>");
        return new Email(subject, html.toString());
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:10px 14px;color:#7a6f63;width:90px\">" + label + "</td><td style=\"padding:10px 14px\"><b>"
                + esc(value) + "</b></td></tr>";
    }

    static String ref(RentalEvent e) {
        return e.contractId() == null ? "" : "CTR-" + e.contractId().toString().replace("-", "").substring(0, 4).toUpperCase();
    }

    private static String shortDates(RentalEvent e) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        return f.format(e.startDate()) + "–" + f.format(e.endDate());
    }

    private static String money(Double amount) {
        return "RWF " + NumberFormat.getIntegerInstance(Locale.US).format(amount == null ? 0 : Math.round(amount));
    }

    private static String orTbc(String s) {
        return s == null ? "to be confirmed" : s;
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "there" : name.trim().split("\\s+")[0];
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
