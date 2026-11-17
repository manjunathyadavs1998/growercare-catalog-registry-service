package com.growercare.catalog.adapters.in.web;

import com.growercare.catalog.adapters.in.web.CatalogViews.BatchReplicaView;
import com.growercare.catalog.adapters.in.web.CatalogViews.ProductReplicaView;
import com.growercare.catalog.adapters.in.web.CatalogViews.RegistrationReplicaView;
import com.growercare.catalog.application.CatalogImport;
import com.growercare.catalog.application.CatalogImport.BatchInput;
import com.growercare.catalog.application.CatalogImport.ProductInput;
import com.growercare.catalog.application.CatalogImport.RegistrationInput;
import com.growercare.catalog.application.CatalogQueries;
import com.growercare.catalog.config.CatalogConfig.CatalogProperties;
import com.growercare.common.error.PermissionDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPEC-CAT-01 R10–R16, R20–R21. gc-security admits only allow-listed service clients on /internal/**; of those, only
 * the import clients (D2) may write (403 otherwise).
 */
@RestController
class InternalCatalogController {

    private final CatalogImport imports;
    private final CatalogQueries queries;
    private final CatalogProperties props;

    InternalCatalogController(CatalogImport imports, CatalogQueries queries, CatalogProperties props) {
        this.imports = imports;
        this.queries = queries;
        this.props = props;
    }

    @PutMapping("/internal/products/{productCode}")
    ResponseEntity<ProductReplicaView> product(Authentication caller, @PathVariable String productCode, @RequestBody ProductInput body) {
        requireImporter(caller);
        var result = imports.product(productCode, body);
        var view = ProductReplicaView.of(queries.replica(result.value().productId()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(view);
    }

    @PutMapping("/internal/products/{productCode}/registrations/{countryCode}")
    ResponseEntity<RegistrationReplicaView> registration(Authentication caller, @PathVariable String productCode,
                                                         @PathVariable String countryCode, @RequestBody RegistrationInput body) {
        requireImporter(caller);
        var result = imports.registration(productCode, countryCode, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(RegistrationReplicaView.of(result.value()));
    }

    @PutMapping("/internal/products/{productCode}/batches/{batchNo}")
    ResponseEntity<BatchReplicaView> batch(Authentication caller, @PathVariable String productCode, @PathVariable String batchNo,
                                           @RequestBody BatchInput body) {
        requireImporter(caller);
        var result = imports.batch(productCode, batchNo, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(BatchReplicaView.of(result.value()));
    }

    @GetMapping("/internal/products/{productId}")
    ProductReplicaView replica(@PathVariable String productId) {
        return ProductReplicaView.of(queries.replica(CatalogViews.id(productId)));
    }

    @GetMapping("/internal/products/{productId}/batches/{batchNo}")
    BatchReplicaView batchReplica(@PathVariable String productId, @PathVariable String batchNo) {
        return BatchReplicaView.of(queries.batch(CatalogViews.id(productId), batchNo));
    }

    /** D2/R16: the service token's principal is the Keycloak client id. */
    private void requireImporter(Authentication caller) {
        if (caller == null || !props.importClientSet().contains(String.valueOf(caller.getPrincipal()))) {
            throw new PermissionDeniedException("catalog:import");
        }
    }
}
