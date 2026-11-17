package com.growercare.catalog.application.port;

import com.growercare.catalog.domain.Catalog.Batch;
import com.growercare.catalog.domain.Catalog.Ingredient;
import com.growercare.catalog.domain.Catalog.Kind;
import com.growercare.catalog.domain.Catalog.Listing;
import com.growercare.catalog.domain.Catalog.Product;
import com.growercare.catalog.domain.Catalog.Registration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** The catalog tables (SPEC-CAT-01 §4). */
public interface CatalogRepository {

    /** Keyset position of a search page: the last product's lower-cased name and id. */
    record After(String name, long productId) { }

    boolean countryExists(String countryCode);

    boolean businessUnitExists(String buCode);

    /** D5: registered, not expired on {@code today}, matching {@code q} (null = all) and {@code kind} (null = all). */
    List<Listing> search(String country, String q, Kind kind, After after, int limit, LocalDate today);

    Optional<Product> product(long productId);

    List<Registration> registrations(long productId);

    Optional<Registration> registration(String country, long productId);

    Optional<Batch> batch(long productId, String batchNo);

    // ------------------------------------------------------------------------------------------- import (locked)

    /** SELECT … FOR UPDATE by business key. */
    Optional<Product> lockProduct(String productCode);

    /** @return the new id, or empty when a concurrent import inserted the code first */
    Optional<Long> insertProduct(String code, String bu, String name, Kind kind, String status, long version);

    void updateProduct(long productId, String bu, String name, Kind kind, String status, long version);

    void replaceIngredients(long productId, List<Ingredient> ingredients);

    void replacePacks(long productId, List<String> packSizes);

    Optional<Registration> lockRegistration(String country, long productId);

    /** Insert or overwrite (the caller decided the version is newer). */
    void saveRegistration(Registration registration);

    Optional<Batch> lockBatch(long productId, String batchNo);

    void saveBatch(Batch batch);
}
