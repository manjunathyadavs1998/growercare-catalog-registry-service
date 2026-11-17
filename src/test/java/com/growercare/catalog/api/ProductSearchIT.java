package com.growercare.catalog.api;

import static com.growercare.catalog.support.TestPrincipals.CARE_BR;
import static com.growercare.catalog.support.TestPrincipals.CARE_IN;
import static com.growercare.catalog.support.TestPrincipals.GROWER_BR1;
import static com.growercare.catalog.support.TestPrincipals.GROWER_IN1;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growercare.catalog.support.CatalogApiTestBase;
import com.growercare.security.GcPrincipal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** SPEC-CAT-01 R1–R5 (CAT-E1…E7): what users read. */
class ProductSearchIT extends CatalogApiTestBase {

    @Test
    void CAT_E1_aGrowerFindsAProductByName() throws Exception {
        search(GROWER_IN1, "country=IN&q=azo").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("Azoxa Duo 325 SC")))
                .andExpect(jsonPath("$.items[0].productId").value("9001"));
    }

    @Test
    void CAT_E1_aCodePrefixAndWildcardsAreLiteral() throws Exception {
        search(GROWER_IN1, "country=IN&q=LMB").andExpect(jsonPath("$.items[*].productId", contains("9002")));
        search(GROWER_IN1, "country=IN&q=%25%25").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void CAT_E2_withoutAQueryEveryRegisteredProductIsListedButNotWithdrawnOnes() throws Exception {
        search(GROWER_IN1, "country=IN").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].productId", hasItem("9001")))
                .andExpect(jsonPath("$.items[*].productId", hasItem("9002")))
                .andExpect(jsonPath("$.items[*].productId", hasItem("9003")))
                .andExpect(jsonPath("$.items[*].productId", not(hasItem("9004"))));
    }

    @Test
    void CAT_E3_brazilOffersOnlyWhatIsRegisteredThere() throws Exception {
        search(GROWER_BR1, "country=BR").andExpect(jsonPath("$.items[*].productId", contains("9001")));
    }

    static Stream<Arguments> hiddenCountries() {
        return Stream.of(
                argumentSet("CAT-E4 a grower in IN asks for BR", GROWER_IN1, "BR"),
                argumentSet("CAT-E4 care.br asks for IN", CARE_BR, "IN"),
                argumentSet("CAT-E4 care.in asks for an unknown country", CARE_IN, "XX"));
    }

    @ParameterizedTest
    @MethodSource("hiddenCountries")
    void countriesOutsideTheCallersReachAre404(GcPrincipal actor, String country) throws Exception {
        problem(search(actor, "country=" + country), 404, "NOT_FOUND");
    }

    @Test
    void CAT_E4_aTerritoryManagerBrowsesTheirHomeCountry() throws Exception {
        search(com.growercare.catalog.support.TestPrincipals.TM_IN, "country=IN").andExpect(status().isOk());
    }

    static Stream<Arguments> products() {
        return Stream.of(
                argumentSet("CAT-E5 9001 in IN", "9001", "IN", 200),
                argumentSet("CAT-E5 9003 is not registered in BR", "9003", "BR", 404),
                argumentSet("CAT-E5 unknown product", "999999", "IN", 404),
                argumentSet("CAT-E5 9004 withdrawn in IN", "9004", "IN", 404));
    }

    @ParameterizedTest
    @MethodSource("products")
    void aProductIsReadInACountryWhereItIsOffered(String productId, String country, int expected) throws Exception {
        var actor = "BR".equals(country) ? GROWER_BR1 : GROWER_IN1;
        var result = as(actor, get("/products/{id}?country={c}", productId, country)).andExpect(status().is(expected));
        if (expected == 200) {
            result.andExpect(jsonPath("$.registration.registrationNo").value("CIR-123456/2024"))
                    .andExpect(jsonPath("$.activeIngredients[0].name").value("Azoxystrobin"))
                    .andExpect(jsonPath("$.packSizes[1]").value("500 mL"));
        }
    }

    static Stream<Arguments> batches() {
        return Stream.of(
                argumentSet("CAT-E6 a batch of the product", "9001", "AZX2604-117", 200),
                argumentSet("CAT-E6 a batch of another product", "9001", "HYC2605-009", 404),
                argumentSet("CAT-E6 an unknown batch", "9001", "NOPE-1", 404));
    }

    @ParameterizedTest
    @MethodSource("batches")
    void aBatchIsCheckedAgainstItsProduct(String productId, String batchNo, int expected) throws Exception {
        var result = as(GROWER_IN1, get("/products/{id}/batches/{b}", productId, batchNo)).andExpect(status().is(expected));
        if (expected == 200) {
            result.andExpect(jsonPath("$.expiryDate").value("2028-04-09")).andExpect(jsonPath("$.status").value("RELEASED"))
                    .andExpect(jsonPath("$.plantCode").doesNotExist());
        }
    }

    @ParameterizedTest(name = "CAT-E7 {0} → 401")
    @ValueSource(strings = {"", "Bearer not-a-jwt"})
    void unauthenticatedCallsAre401(String authorization) throws Exception {
        var request = get("/products?country=IN");
        if (!authorization.isEmpty()) {
            request.header("Authorization", authorization);
        }
        mvc.perform(request).andExpect(status().isUnauthorized());
    }

    /** Fixtures in IN with names no other row searches for (rows stay independent; CAT-E3 lists all of BR). */
    @Test
    void CAT_E1_pagesFollowTheCursor() throws Exception {
        for (int i = 0; i < 21; i++) {
            var code = "PAGE-" + i;
            importAs(ERP, "/internal/products/" + code, product("Paging product " + "%02d".formatted(i), 1)).andExpect(status().is2xxSuccessful());
            importAs(ERP, "/internal/products/" + code + "/registrations/IN",
                    java.util.Map.of("registrationNo", "IN-P" + i, "status", "REGISTERED", "version", 1)).andExpect(status().is2xxSuccessful());
        }
        var first = search(GROWER_IN1, "country=IN&q=paging").andExpect(jsonPath("$.items.length()").value(20))
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(first, "$.nextCursor");
        search(GROWER_IN1, "country=IN&q=paging&cursor=" + cursor).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Paging product 20"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        problem(search(GROWER_IN1, "country=IN&cursor=%21%21"), 400, "VALIDATION_FAILED");
    }
}
