package com.growercare.catalog.application;

import com.growercare.catalog.application.port.CatalogRepository;
import com.growercare.catalog.application.port.CatalogRepository.After;
import com.growercare.catalog.domain.Catalog.Batch;
import com.growercare.catalog.domain.Catalog.Kind;
import com.growercare.catalog.domain.Catalog.Listing;
import com.growercare.catalog.domain.Catalog.Product;
import com.growercare.catalog.domain.Catalog.Registration;
import com.growercare.catalog.domain.CatalogRules;
import com.growercare.catalog.domain.CountryAccess;
import com.growercare.common.error.NotFoundException;
import com.growercare.security.web.CurrentPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** User and service reads: SPEC-CAT-01 R1–R4, R20, R21. */
@Service
public class CatalogQueries {

    public static final int PAGE_SIZE = 20;
    /** D5: shorter queries list everything registered. */
    public static final int MIN_QUERY = 2;

    /** One search page; {@code next} is the keyset position for the following page, or null. */
    public record Page(List<Listing> items, After next) { }

    /** A product with every country's registration (R20). */
    public record ProductReplica(Product product, List<Registration> registrations) { }

    private final CatalogRepository catalog;
    private final Clock clock;

    public CatalogQueries(CatalogRepository catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    /** R1/R2: registered products of a country the caller may browse. */
    public Page search(String country, String q, String kind, After after) {
        requireBrowsable(country);
        var k = kind == null ? null : CatalogRules.oneOf(Kind.class, kind, "kind");
        var query = q == null || q.strip().length() < MIN_QUERY ? null : q.strip();
        var rows = catalog.search(country, query, k, after, PAGE_SIZE + 1, today());
        if (rows.size() <= PAGE_SIZE) {
            return new Page(rows, null);
        }
        var page = rows.subList(0, PAGE_SIZE);
        var last = page.getLast().product();
        return new Page(page, new After(last.name().toLowerCase(java.util.Locale.ROOT), last.productId()));
    }

    /** R3: the product with its registration in that country; 404 unless offered there (D5). */
    public Listing product(long productId, String country) {
        requireBrowsable(country);
        var registration = catalog.registration(country, productId).filter(r -> r.offeredOn(today()))
                .orElseThrow(() -> new NotFoundException("Product"));
        var product = catalog.product(productId).orElseThrow(() -> new NotFoundException("Product"));
        return new Listing(product, registration);
    }

    /** R4 / R21: is this batch one of that product's? (D6: never a list). */
    public Batch batch(long productId, String batchNo) {
        return catalog.batch(productId, batchNo).orElseThrow(() -> new NotFoundException("Batch"));
    }

    /** R20: for service clients. */
    public ProductReplica replica(long productId) {
        var product = catalog.product(productId).orElseThrow(() -> new NotFoundException("Product"));
        return new ProductReplica(product, catalog.registrations(productId));
    }

    /** D4: unknown countries and countries outside the caller's reach are the same 404. */
    private void requireBrowsable(String country) {
        if (country == null || !CountryAccess.mayBrowse(CurrentPrincipal.require(), country, clock.instant())
                || !catalog.countryExists(country)) {
            throw new NotFoundException("Country");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
