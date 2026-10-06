package co.tkm.ticketmanagementsystem.service;

import java.security.SecureRandom;

/**
 * The short code a customer reads off the success screen or email and types back in
 * to find a booking (G29). Eight characters from an alphabet without {@code 0/O/1/I},
 * so it survives being read aloud or copied by hand: 32^8 ≈ 10^12 values.
 */
public final class BookingReference {

    public static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReference() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Upper-cased with spaces and dashes removed; null when nothing is left. */
    public static String normalize(String input) {
        if (input == null) return null;
        String value = input.replaceAll("[\\s-]", "").toUpperCase();
        return value.isEmpty() ? null : value;
    }

    /**
     * A phone number reduced to what identifies it: digits only, without Cambodia's
     * {@code 855} country code or the trunk {@code 0}, so "012 345 678",
     * "+855 12-345 678", and "85512345678" all compare equal.
     */
    public static String phoneDigits(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("855")) digits = digits.substring(3);
        return digits.replaceFirst("^0+", "");
    }
}
