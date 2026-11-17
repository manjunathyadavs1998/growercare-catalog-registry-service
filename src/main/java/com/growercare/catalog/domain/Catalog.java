package com.growercare.catalog.domain;

import java.time.LocalDate;
import java.util.List;

/** The catalog's records (SPEC-CAT-01 §4). Not personal data. */
public final class Catalog {

    private Catalog() { }

    public enum Kind { FUNGICIDE, INSECTICIDE, HERBICIDE, SEED, BIOLOGICAL }

    public enum ProductStatus { ACTIVE, DISCONTINUED }

    public enum RegistrationStatus { REGISTERED, SUSPENDED, WITHDRAWN }

    public enum BatchStatus { RELEASED, BLOCKED, RECALLED }

    public record Ingredient(String name, String concentration) { }

    public record Product(long productId, String productCode, String buCode, String name, Kind kind, ProductStatus status,
                          long version, List<Ingredient> ingredients, List<String> packSizes) {
        public Product {
            ingredients = List.copyOf(ingredients);
            packSizes = List.copyOf(packSizes);
        }
    }

    public record Registration(String countryCode, long productId, String registrationNo, RegistrationStatus status,
                               LocalDate validUntil, long version) {

        /** D5: what a search offers — registered and not expired on {@code today}. */
        public boolean offeredOn(LocalDate today) {
            return status == RegistrationStatus.REGISTERED && (validUntil == null || !validUntil.isBefore(today));
        }
    }

    public record Batch(long productId, String batchNo, LocalDate mfgDate, LocalDate expiryDate, String plantCode,
                        BatchStatus status, long version) { }

    /** A search hit: the product and its registration in the searched country. */
    public record Listing(Product product, Registration registration) { }
}
