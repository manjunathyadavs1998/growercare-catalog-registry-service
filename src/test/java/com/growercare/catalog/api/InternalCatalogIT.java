package com.growercare.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growercare.catalog.support.CatalogApiTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SPEC-CAT-01 R20–R21 (CAT-E20, E21): what case-service replicates. */
class InternalCatalogIT extends CatalogApiTestBase {

    @Test
    void CAT_E20_theProductReplicaHasEveryRegistration() throws Exception {
        mvc.perform(get("/internal/products/9001").header("Authorization", CASE_SERVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("AZX-325SC"))
                .andExpect(jsonPath("$.buCode").value("CP"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.registrations.length()").value(2))
                .andExpect(jsonPath("$.registrations[0].countryCode").value("BR"))
                .andExpect(jsonPath("$.registrations[1].countryCode").value("IN"));
    }

    @ParameterizedTest(name = "CAT-E21 {0}/{1} → {2}")
    @CsvSource(textBlock = """
            9001, AZX2604-117, 200
            9001, NOPE-0001,   404
            999,  AZX2604-117, 404
            abc,  AZX2604-117, 404
            """)
    void theBatchReplica(String productId, String batchNo, int expected) throws Exception {
        var result = mvc.perform(get("/internal/products/{id}/batches/{b}", productId, batchNo).header("Authorization", CASE_SERVICE))
                .andExpect(status().is(expected));
        if (expected == 200) {
            result.andExpect(jsonPath("$.plantCode").value("IN-GOA")).andExpect(jsonPath("$.version").value(1));
        }
    }
}
