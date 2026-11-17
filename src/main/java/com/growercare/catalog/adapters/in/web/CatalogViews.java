package com.growercare.catalog.adapters.in.web;

import com.growercare.catalog.application.CatalogQueries.ProductReplica;
import com.growercare.catalog.application.port.CatalogRepository.After;
import com.growercare.catalog.domain.Catalog.Batch;
import com.growercare.catalog.domain.Catalog.Ingredient;
import com.growercare.catalog.domain.Catalog.Listing;
import com.growercare.catalog.domain.Catalog.Product;
import com.growercare.catalog.domain.Catalog.Registration;
import com.growercare.common.error.NotFoundException;
import com.growercare.common.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/** JSON shapes of SPEC-CAT-01 §3. Ids are strings. */
final class CatalogViews {

    private CatalogViews() { }

    record RegistrationView(String countryCode, String registrationNo, String status, LocalDate validUntil) {
        static RegistrationView of(Registration r) {
            return new RegistrationView(r.countryCode(), r.registrationNo(), r.status().name(), r.validUntil());
        }
    }

    record ProductSummary(String productId, String productCode, String buCode, String name, String kind) {
        static ProductSummary of(Product p) {
            return new ProductSummary(Long.toString(p.productId()), p.productCode(), p.buCode(), p.name(), p.kind().name());
        }
    }

    record ProductPage(List<ProductSummary> items, String nextCursor) { }

    record ProductView(String productId, String productCode, String buCode, String name, String kind, String status,
                       List<Ingredient> activeIngredients, List<String> packSizes, RegistrationView registration) {
        static ProductView of(Listing l) {
            var p = l.product();
            return new ProductView(Long.toString(p.productId()), p.productCode(), p.buCode(), p.name(), p.kind().name(),
                    p.status().name(), p.ingredients(), p.packSizes(), RegistrationView.of(l.registration()));
        }
    }

    record BatchView(String productId, String batchNo, LocalDate mfgDate, LocalDate expiryDate, String status) {
        static BatchView of(Batch b) {
            return new BatchView(Long.toString(b.productId()), b.batchNo(), b.mfgDate(), b.expiryDate(), b.status().name());
        }
    }

    /** R20 / R10: the full product for service clients, with every country's registration. */
    record ProductReplicaView(String productId, String productCode, String buCode, String name, String kind, String status,
                              long version, List<Ingredient> activeIngredients, List<String> packSizes,
                              List<RegistrationReplicaView> registrations) {
        /** Registrations carry their own version, so a replica can guard them separately (SPEC-CASE-08 D3). */
        static ProductReplicaView of(ProductReplica r) {
            var p = r.product();
            return new ProductReplicaView(Long.toString(p.productId()), p.productCode(), p.buCode(), p.name(), p.kind().name(),
                    p.status().name(), p.version(), p.ingredients(), p.packSizes(),
                    r.registrations().stream().map(RegistrationReplicaView::of).toList());
        }
    }

    record RegistrationReplicaView(String productId, String countryCode, String registrationNo, String status, LocalDate validUntil,
                                   long version) {
        static RegistrationReplicaView of(Registration r) {
            return new RegistrationReplicaView(Long.toString(r.productId()), r.countryCode(), r.registrationNo(), r.status().name(),
                    r.validUntil(), r.version());
        }
    }

    record BatchReplicaView(String productId, String batchNo, LocalDate mfgDate, LocalDate expiryDate, String plantCode, String status,
                            long version) {
        static BatchReplicaView of(Batch b) {
            return new BatchReplicaView(Long.toString(b.productId()), b.batchNo(), b.mfgDate(), b.expiryDate(), b.plantCode(),
                    b.status().name(), b.version());
        }
    }

    // ------------------------------------------------------------------------------------------------ ids, cursors

    static long id(String raw) {
        if (raw == null || !raw.matches("^[1-9][0-9]{0,18}$")) {
            throw new NotFoundException("Product");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new NotFoundException("Product");
        }
    }

    static String cursor(After after) {
        if (after == null) {
            return null;
        }
        var text = after.name() + '\u0000' + after.productId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static After after(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            var text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = text.lastIndexOf('\u0000');
            return new After(text.substring(0, sep), Long.parseLong(text.substring(sep + 1)));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw ValidationException.of("cursor", "is not a cursor returned by this API");
        }
    }
}
