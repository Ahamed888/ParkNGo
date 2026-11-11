package com.example.parkngo.util;

import java.util.regex.Pattern;

/**
 * Dedicated validator for official Sri Lankan vehicle license plates (Department of Motor Traffic - DMT).
 * 
 * Supported Sri Lankan Formats:
 * 1. Modern Provincial Format (Post-2000):
 *    - 2-letter Province prefix: WP, CP, SP, NW, NC, EP, SG, VA, UW
 *    - Optional space or hyphen separator
 *    - 2 or 3 English Letters (e.g. CA, CAB, KA, GA, BZ)
 *    - Hyphen or space separator
 *    - 4 digits (0001 - 9999)
 *    Examples: "WP CA-1234", "WP-CAB-5678", "CP KA-9012", "CAB-5678", "KV-1234"
 * 
 * 2. Pre-Provincial Numeric Format (Sri / Classic / Dash numbers):
 *    - 1 to 3 digits series code (e.g. 19, 64, 65, 300, 301)
 *    - Hyphen or space separator
 *    - 4 digits (0001 - 9999)
 *    Examples: "19-1234", "65-5678", "300-9999"
 */
public final class SriLankanPlateValidator {

    // Regex for Modern Provincial: (WP|CP|SP|NW|NC|EP|SG|VA|UW)?[ -]?[A-Z]{1,4}[ -]?\d{1,4}
    // E.g. WP CA-1234, WP-CAB-5678, CP KA-9012, CAB-5678, KV-1234, WP-1234
    public static final String MODERN_OR_SERIES_REGEX =
            "^(?:(WP|CP|SP|NW|NC|EP|SG|VA|UW)[\\s-]*)?(?:([A-Z]{1,4})[\\s-]*)?(\\d{1,4})$";

    // Regex for Pre-Provincial / Sri Numbers: \d{1,3}[ -]?(?:SRI[ -]?)?\d{1,4}
    // E.g. 19-1234, 301-4567, 65-8901, 19 SRI 1234
    public static final String PRE_PROVINCIAL_NUMERIC_REGEX =
            "^(\\d{1,3})[\\s-]*(?:SRI[\\s-]*)?(\\d{1,4})$";

    // Combined pattern accepting both formats, or general alphanumeric plate strings
    public static final String COMBINED_LK_REGEX =
            "^(?:(?:(WP|CP|SP|NW|NC|EP|SG|VA|UW)[\\s-]*)?(?:([A-Z]{1,4})|(\\d{1,3}))[\\s-]*(?:SRI[\\s-]*)?(\\d{1,4})|(?:(WP|CP|SP|NW|NC|EP|SG|VA|UW)[\\s-]*(\\d{1,4}))|[A-Z0-9\\s-]{3,15})$";

    private static final Pattern MODERN_OR_SERIES_PATTERN =
            Pattern.compile(MODERN_OR_SERIES_REGEX, Pattern.CASE_INSENSITIVE);

    private static final Pattern PRE_PROVINCIAL_NUMERIC_PATTERN =
            Pattern.compile(PRE_PROVINCIAL_NUMERIC_REGEX, Pattern.CASE_INSENSITIVE);

    private static final Pattern COMBINED_PATTERN =
            Pattern.compile(COMBINED_LK_REGEX, Pattern.CASE_INSENSITIVE);

    private SriLankanPlateValidator() {}

    /**
     * Checks if the given plate number conforms to official Sri Lankan registration syntax.
     */
    public static boolean isValid(String plate) {
        if (plate == null) return false;
        String clean = plate.trim().toUpperCase();
        if (clean.length() < 3 || clean.length() > 16) return false;

        return COMBINED_PATTERN.matcher(clean).matches();
    }

    /**
     * Standardizes a valid Sri Lankan plate into the official normalized presentation format.
     * E.g. "wp ca1234" -> "WP CA-1234", "cab5678" -> "CAB-5678", "191234" -> "19-1234"
     */
    public static String normalize(String plate) {
        if (plate == null) return "";
        String clean = plate.trim().toUpperCase();

        var modernMatcher = MODERN_OR_SERIES_PATTERN.matcher(clean);
        if (modernMatcher.matches()) {
            String province = modernMatcher.group(1);
            String letters = modernMatcher.group(2);
            String numbers = modernMatcher.group(3);

            if (province != null && !province.isEmpty()) {
                return province.toUpperCase() + " " + letters.toUpperCase() + "-" + numbers;
            } else {
                return letters.toUpperCase() + "-" + numbers;
            }
        }

        var numericMatcher = PRE_PROVINCIAL_NUMERIC_PATTERN.matcher(clean);
        if (numericMatcher.matches()) {
            String prefix = numericMatcher.group(1);
            String numbers = numericMatcher.group(2);
            return prefix + "-" + numbers;
        }

        return clean;
    }
}
