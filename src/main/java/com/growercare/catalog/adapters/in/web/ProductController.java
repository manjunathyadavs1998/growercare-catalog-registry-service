package com.growercare.catalog.adapters.in.web;

import com.growercare.catalog.adapters.in.web.CatalogViews.BatchView;
import com.growercare.catalog.adapters.in.web.CatalogViews.ProductPage;
import com.growercare.catalog.adapters.in.web.CatalogViews.ProductSummary;
import com.growercare.catalog.adapters.in.web.CatalogViews.ProductView;
import com.growercare.catalog.application.CatalogQueries;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SPEC-CAT-01 R1–R5: what users (the apps) read. Any authenticated GrowerCare user; country limited by D4. */
@RestController
class ProductController {

    private final CatalogQueries queries;

    ProductController(CatalogQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/products")
    ProductPage search(@RequestParam String country, @RequestParam(required = false) String q,
                       @RequestParam(required = false) String kind, @RequestParam(required = false) String cursor) {
        var page = queries.search(country, q, kind, CatalogViews.after(cursor));
        return new ProductPage(page.items().stream().map(l -> ProductSummary.of(l.product())).toList(), CatalogViews.cursor(page.next()));
    }

    @GetMapping("/products/{productId}")
    ProductView product(@PathVariable String productId, @RequestParam String country) {
        return ProductView.of(queries.product(CatalogViews.id(productId), country));
    }

    /** D6: a check, never a list. */
    @GetMapping("/products/{productId}/batches/{batchNo}")
    BatchView batch(@PathVariable String productId, @PathVariable String batchNo) {
        return BatchView.of(queries.batch(CatalogViews.id(productId), batchNo));
    }
}
