package co.tkm.ticketmanagementsystem.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** The one email check the API uses (reminders, guest checkout). */
public final class EmailAddress {

    private static final Pattern PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private EmailAddress() {
    }

    /** Trimmed and lower-cased; empty for null. */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return PATTERN.matcher(normalized).matches();
    }
}
