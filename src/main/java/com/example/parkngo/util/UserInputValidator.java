package com.example.parkngo.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared syntax and normalization rules for account-facing input. */
public final class UserInputValidator {
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern MOBILE = Pattern.compile("^(?:07\\d{8}|\\+947\\d{8})$");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$");

    private UserInputValidator() {}

    public static String cleanName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public static String cleanEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String cleanPhone(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\s-]", "");
    }

    public static boolean isValidName(String value) {
        String clean = cleanName(value);
        return clean.length() >= 2 && clean.length() <= 60 && NAME.matcher(clean).matches();
    }

    public static boolean isValidEmail(String value) {
        String clean = cleanEmail(value);
        return clean.length() <= 100 && EMAIL.matcher(clean).matches();
    }

    public static boolean isValidSriLankanMobile(String value) {
        return MOBILE.matcher(cleanPhone(value)).matches();
    }

    public static boolean isStrongPassword(String value) {
        return value != null && PASSWORD.matcher(value).matches();
    }
}
