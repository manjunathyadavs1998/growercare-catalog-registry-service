package com.growercare.catalog.domain;

import com.growercare.common.error.ValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Import input rules (SPEC-CAT-01 R14): every violation is a 400 naming the field. Framework-free. */
public final class CatalogRules {

    private CatalogRules() { }

    public static final Pattern PRODUCT_CODE = Pattern.compile("^[A-Z0-9-]{3,32}$");
    public static final Pattern BATCH_NO = Pattern.compile("^[A-Z0-9-]{4,32}$");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    public static String productCode(String raw) {
        return matching(raw, PRODUCT_CODE, "productCode", "3-32 characters A-Z, 0-9 or -");
    }

    public static String batchNo(String raw) {
        return matching(raw, BATCH_NO, "batchNo", "4-32 characters A-Z, 0-9 or -");
    }

    /** Non-blank text of at most {@code max} characters without control characters; returned stripped. */
    public static String text(String raw, int max, String field) {
        if (raw == null || raw.isBlank() || raw.strip().length() > max || CONTROL.matcher(raw).find()) {
            throw ValidationException.of(field, "must be 1-" + max + " characters without control characters");
        }
        return raw.strip();
    }

    public static <E extends Enum<E>> E oneOf(Class<E> type, String raw, String field) {
        for (var value : type.getEnumConstants()) {
            if (value.name().equals(raw)) {
                return value;
            }
        }
        throw ValidationException.of(field, "must be one of " + Arrays.toString(type.getEnumConstants()));
    }

    public static long version(Long raw) {
        if (raw == null || raw < 1) {
            throw ValidationException.of("version", "is required: the source system's version, 1 or more");
        }
        return raw;
    }

    public static LocalDate date(String raw, String field, boolean required) {
        if (raw == null) {
            if (required) {
                throw ValidationException.of(field, "is required (yyyy-MM-dd)");
            }
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw ValidationException.of(field, "must be a date (yyyy-MM-dd)");
        }
    }

    /** R14: a batch expires after it is made. */
    public static void expiryAfterMfg(LocalDate mfg, LocalDate expiry) {
        if (!expiry.isAfter(mfg)) {
            throw ValidationException.of("expiryDate", "must be after mfgDate");
        }
    }

    private static String matching(String raw, Pattern pattern, String field, String rule) {
        if (raw == null || !pattern.matcher(raw).matches()) {
            throw ValidationException.of(field, "must be " + rule);
        }
        return raw;
    }
}
