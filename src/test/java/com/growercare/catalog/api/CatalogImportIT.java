package com.growercare.catalog.api;

import static com.growercare.catalog.support.TestPrincipals.GROWER_IN1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growercare.catalog.support.CatalogApiTestBase;
import com.growercare.testsupport.TestJwts;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** SPEC-CAT-01 R10–R16 (CAT-E10…E16): the ERP / regulatory import. */
class CatalogImportIT extends CatalogApiTestBase {

    String created(String code) throws Exception {
        var json = importAs(ERP, "/internal/products/" + code, product("Test " + code, 1)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.productId");
    }

    @Test
    void CAT_E10_aNewProductGetsAnIdAndANewerVersionUpdatesIt() throws Exception {
        var code = freshCode();
        var id = created(code);
        assertThat(Long.parseLong(id)).isGreaterThanOrEqualTo(100000);
        importAs(ERP, "/internal/products/" + code, product("Renamed " + code, 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed " + code)).andExpect(jsonPath("$.version").value(2));
        assertThat(events(id)).hasSize(2).allSatisfy(e -> assertThat(e).startsWith("catalog.product.upserted"));
    }

    @Test
    void CAT_E11_replayedAndStaleVersionsChangeNothing() throws Exception {
        var code = freshCode();
        var id = created(code);
        importAs(ERP, "/internal/products/" + code, product("Renamed " + code, 2)).andExpect(status().isOk());
        importAs(ERP, "/internal/products/" + code, product("Again " + code, 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed " + code));
        importAs(ERP, "/internal/products/" + code, product("Older " + code, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        assertThat(events(id)).hasSize(2);
    }

    @Test
    void CAT_E12_registrationChangesDecideWhatIsOffered() throws Exception {
        var code = freshCode();
        var id = created(code);
        var path = "/internal/products/" + code + "/registrations/IN";
        importAs(ERP, path, Map.of("registrationNo", "CIR-T-" + id, "status", "REGISTERED", "validUntil", "2030-01-01", "version", 1))
                .andExpect(status().isCreated());
        search(GROWER_IN1, "country=IN&q=" + code).andExpect(jsonPath("$.items[*].productId", hasItem(id)));
        importAs(ERP, path, Map.of("registrationNo", "CIR-T-" + id, "status", "SUSPENDED", "validUntil", "2030-01-01", "version", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        search(GROWER_IN1, "country=IN&q=" + code).andExpect(jsonPath("$.items[*].productId", not(hasItem(id))));
        assertThat(events(id)).filteredOn(e -> e.startsWith("catalog.registration.changed")).hasSize(2)
                .last().satisfies(e -> assertThat(e).contains("\"status\": \"SUSPENDED\"", "\"countryCode\": \"IN\""));
    }

    @Test
    void CAT_E13_batchesAreUpsertedWithEvents() throws Exception {
        var code = freshCode();
        var id = created(code);
        var path = "/internal/products/" + code + "/batches/TST1-0001";
        var batch = new HashMap<String, Object>(Map.of("mfgDate", "2026-01-01", "expiryDate", "2028-01-01", "plantCode", "IN-GOA",
                "status", "RELEASED", "version", 1));
        importAs(ERP, path, batch).andExpect(status().isCreated());
        batch.put("status", "RECALLED");
        batch.put("version", 2);
        importAs(ERP, path, batch).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECALLED"));
        assertThat(events(id)).filteredOn(e -> e.startsWith("catalog.batch.upserted")).hasSize(2)
                .allSatisfy(e -> assertThat(e).contains("\"batchNo\": \"TST1-0001\""));
    }

    static Stream<Arguments> invalidProducts() {
        return Stream.of(
                argumentSet("CAT-E14 unknown BU", "OK-CODE", Map.of("buCode", "XX"), "buCode"),
                argumentSet("CAT-E14 unknown kind", "OK-CODE", Map.of("kind", "PAINT"), "kind"),
                argumentSet("CAT-E14 lower-case code", "t1", Map.of(), "productCode"),
                argumentSet("CAT-E14 no version", "OK-CODE", Map.of("version", 0), "version"));
    }

    @ParameterizedTest
    @MethodSource("invalidProducts")
    void invalidProductsAre400NamingTheField(String code, Map<String, Object> change, String field) throws Exception {
        var body = new HashMap<>(product("Invalid", 1));
        body.putAll(change);
        problem(importAs(ERP, "/internal/products/" + code, body), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    static Stream<Arguments> invalidChildren() {
        return Stream.of(
                argumentSet("CAT-E14 batch number too short", "/batches/ab",
                        Map.of("mfgDate", "2026-01-01", "expiryDate", "2027-01-01", "plantCode", "P", "status", "RELEASED", "version", 1), "batchNo"),
                argumentSet("CAT-E14 expiry before manufacture", "/batches/GOOD-0001",
                        Map.of("mfgDate", "2026-01-01", "expiryDate", "2025-01-01", "plantCode", "P", "status", "RELEASED", "version", 1), "expiryDate"),
                argumentSet("CAT-E14 unknown country", "/registrations/XX",
                        Map.of("registrationNo", "R-1", "status", "REGISTERED", "version", 1), "country"));
    }

    @ParameterizedTest
    @MethodSource("invalidChildren")
    void invalidRegistrationsAndBatchesAre400(String suffix, Map<String, Object> body, String field) throws Exception {
        problem(importAs(ERP, "/internal/products/AZX-325SC" + suffix, body), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    @Test
    void CAT_E15_aRegistrationForAnUnknownProductIs404() throws Exception {
        problem(importAs(ERP, "/internal/products/NOPE/registrations/IN", Map.of("registrationNo", "R-1", "status", "REGISTERED", "version", 1)),
                404, "NOT_FOUND");
    }

    static Stream<Arguments> refusedImporters() {
        return Stream.of(
                argumentSet("CAT-E16 a user token", TestJwts.user(TestJwts.CARE_IN).bearer(), 403),
                argumentSet("CAT-E16 case-service (reader, not importer)", CASE_SERVICE, 403),
                argumentSet("CAT-E16 a client not allow-listed", TestJwts.service("integration-service").bearer(), 401),
                argumentSet("CAT-E16 no token", null, 401));
    }

    @ParameterizedTest
    @MethodSource("refusedImporters")
    void onlyImportClientsWrite(String authorization, int expected) throws Exception {
        importAs(authorization, "/internal/products/" + freshCode(), product("Refused", 1)).andExpect(status().is(expected));
    }
}
