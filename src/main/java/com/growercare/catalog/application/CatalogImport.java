package com.growercare.catalog.application;

import com.growercare.catalog.application.port.CatalogRepository;
import com.growercare.catalog.domain.Catalog.Batch;
import com.growercare.catalog.domain.Catalog.BatchStatus;
import com.growercare.catalog.domain.Catalog.Ingredient;
import com.growercare.catalog.domain.Catalog.Kind;
import com.growercare.catalog.domain.Catalog.Product;
import com.growercare.catalog.domain.Catalog.ProductStatus;
import com.growercare.catalog.domain.Catalog.Registration;
import com.growercare.catalog.domain.Catalog.RegistrationStatus;
import com.growercare.catalog.domain.CatalogRules;
import com.growercare.common.error.NotFoundException;
import com.growercare.common.error.ValidationException;
import com.growercare.common.web.TraceIds;
import com.growercare.outbox.OutboxWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SPEC-CAT-01 R10–R15: idempotent upserts from the ERP / regulatory sync (D2). A version not newer than the stored one
 * changes nothing and publishes nothing (R11), so a replayed or reordered sync is harmless.
 */
@Service
public class CatalogImport {

    static final String OUTBOX = "catalog.outbox";

    public record IngredientInput(String name, String concentration) { }

    public record ProductInput(String buCode, String name, String kind, List<IngredientInput> activeIngredients,
                               List<String> packSizes, String status, Long version) { }

    public record RegistrationInput(String registrationNo, String status, String validUntil, Long version) { }

    public record BatchInput(String mfgDate, String expiryDate, String plantCode, String status, Long version) { }

    /** What an upsert did: whether the row is new (201) and the row as stored now. */
    public record Result<T>(T value, boolean created) { }

    private final CatalogRepository catalog;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public CatalogImport(CatalogRepository catalog, OutboxWriter outbox, TransactionTemplate tx) {
        this.catalog = catalog;
        this.outbox = outbox;
        this.tx = tx;
    }

    public Result<Product> product(String rawCode, ProductInput in) {
        var code = CatalogRules.productCode(rawCode);
        var bu = in.buCode();
        if (bu == null || !catalog.businessUnitExists(bu)) {
            throw ValidationException.of("buCode", "is not a known business unit");
        }
        var name = CatalogRules.text(in.name(), 120, "name");
        var kind = CatalogRules.oneOf(Kind.class, in.kind(), "kind");
        var status = CatalogRules.oneOf(ProductStatus.class, in.status(), "status");
        var version = CatalogRules.version(in.version());
        var ingredients = ingredients(in.activeIngredients());
        var packs = packs(in.packSizes());

        return tx.execute(s -> {
            var current = catalog.lockProduct(code);
            if (current.isEmpty()) {
                var id = catalog.insertProduct(code, bu, name, kind, status.name(), version);
                if (id.isPresent()) {
                    catalog.replaceIngredients(id.get(), ingredients);
                    catalog.replacePacks(id.get(), packs);
                    productEvent(id.get(), version);
                    return new Result<>(catalog.product(id.get()).orElseThrow(), true);
                }
                current = catalog.lockProduct(code);                    // a concurrent import inserted it: update path
            }
            var existing = current.orElseThrow();
            if (version <= existing.version()) {
                return new Result<>(existing, false);                   // R11
            }
            catalog.updateProduct(existing.productId(), bu, name, kind, status.name(), version);
            catalog.replaceIngredients(existing.productId(), ingredients);
            catalog.replacePacks(existing.productId(), packs);
            productEvent(existing.productId(), version);
            return new Result<>(catalog.product(existing.productId()).orElseThrow(), false);
        });
    }

    public Result<Registration> registration(String rawCode, String country, RegistrationInput in) {
        var code = CatalogRules.productCode(rawCode);
        if (country == null || !catalog.countryExists(country)) {
            throw ValidationException.of("country", "is not a known country");
        }
        var number = CatalogRules.text(in.registrationNo(), 40, "registrationNo");
        var status = CatalogRules.oneOf(RegistrationStatus.class, in.status(), "status");
        var validUntil = CatalogRules.date(in.validUntil(), "validUntil", false);
        var version = CatalogRules.version(in.version());

        return tx.execute(s -> {
            var product = catalog.lockProduct(code).orElseThrow(() -> new NotFoundException("Product"));     // R15
            var current = catalog.lockRegistration(country, product.productId());
            if (current.isPresent() && version <= current.get().version()) {
                return new Result<>(current.get(), false);
            }
            var next = new Registration(country, product.productId(), number, status, validUntil, version);
            catalog.saveRegistration(next);
            boolean changed = current.isEmpty() || current.get().status() != status || !Objects.equals(current.get().validUntil(), validUntil);
            if (changed) {
                event("product", product.productId(), "catalog.registration.changed",
                        Map.of("productId", Long.toString(product.productId()), "countryCode", country, "status", status.name()));
            }
            return new Result<>(next, current.isEmpty());
        });
    }

    public Result<Batch> batch(String rawCode, String rawBatchNo, BatchInput in) {
        var code = CatalogRules.productCode(rawCode);
        var batchNo = CatalogRules.batchNo(rawBatchNo);
        var mfg = CatalogRules.date(in.mfgDate(), "mfgDate", true);
        var expiry = CatalogRules.date(in.expiryDate(), "expiryDate", true);
        CatalogRules.expiryAfterMfg(mfg, expiry);
        var plant = CatalogRules.text(in.plantCode(), 16, "plantCode");
        var status = CatalogRules.oneOf(BatchStatus.class, in.status(), "status");
        var version = CatalogRules.version(in.version());

        return tx.execute(s -> {
            var product = catalog.lockProduct(code).orElseThrow(() -> new NotFoundException("Product"));     // R15
            var current = catalog.lockBatch(product.productId(), batchNo);
            if (current.isPresent() && version <= current.get().version()) {
                return new Result<>(current.get(), false);
            }
            var next = new Batch(product.productId(), batchNo, mfg, expiry, plant, status, version);
            catalog.saveBatch(next);
            var payload = new LinkedHashMap<String, Object>();
            payload.put("productId", Long.toString(product.productId()));
            payload.put("batchNo", batchNo);
            payload.put("version", version);
            event("batch", product.productId(), "catalog.batch.upserted", payload);
            return new Result<>(next, current.isEmpty());
        });
    }

    private void productEvent(long productId, long version) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("productId", Long.toString(productId));
        payload.put("version", version);
        event("product", productId, "catalog.product.upserted", payload);
    }

    /** D7: ids and codes only; the key is the product id, so one product's events stay ordered. */
    private void event(String aggregate, long productId, String type, Map<String, Object> payload) {
        outbox.append(OUTBOX, new OutboxWriter.Event(aggregate, Long.toString(productId), type, payload), TraceIds.currentOrNull());
    }

    private static List<Ingredient> ingredients(List<IngredientInput> raw) {
        var out = new ArrayList<Ingredient>();
        if (raw == null) {
            return out;
        }
        for (int i = 0; i < raw.size(); i++) {
            var item = raw.get(i);
            if (item == null) {
                throw ValidationException.of("activeIngredients[" + i + "]", "must be an object");
            }
            out.add(new Ingredient(CatalogRules.text(item.name(), 120, "activeIngredients[" + i + "].name"),
                    CatalogRules.text(item.concentration(), 40, "activeIngredients[" + i + "].concentration")));
        }
        return out;
    }

    private static List<String> packs(List<String> raw) {
        var out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        for (int i = 0; i < raw.size(); i++) {
            out.add(CatalogRules.text(raw.get(i), 40, "packSizes[" + i + "]"));
        }
        return out;
    }
}
