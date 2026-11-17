package com.growercare.catalog.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growercare.security.GcPrincipal;
import com.growercare.security.PrincipalResolver;
import com.growercare.testsupport.PostgresTestDatabase;
import com.growercare.testsupport.TestJwts;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared Spring context for catalog API tests: a real Postgres (owner/app/CDC roles, Flyway with the dev seed as the
 * owner), test JWTs, and IAM replaced by {@link TestPrincipals}.
 */
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Import(CatalogApiTestBase.Stubs.class)
public abstract class CatalogApiTestBase {

    protected static final PostgresTestDatabase DB = PostgresTestDatabase.forService("gc_catalog");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::jdbcUrl);
        r.add("spring.datasource.username", DB::appUser);
        r.add("spring.datasource.password", DB::appPassword);
        r.add("spring.flyway.user", DB::ownerUser);
        r.add("spring.flyway.password", DB::ownerPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev-seed");
        r.add("spring.flyway.placeholders.cdc_role", () -> PostgresTestDatabase.CDC_ROLE);
    }

    @TestConfiguration
    public static class Stubs {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return TestJwts.decoder(List.of("gc-api", "gc-internal"));
        }

        @Bean
        @Primary
        PrincipalResolver stubIam() {
            return TestPrincipals::resolve;
        }
    }

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String ERP = TestJwts.service("erp-sync").bearer();
    protected static final String CASE_SERVICE = TestJwts.service("case-service").bearer();
    private static final AtomicInteger NEXT_CODE = new AtomicInteger(1);

    @Autowired
    protected MockMvc mvc;

    protected final JdbcClient owner = JdbcClient.create(DB.ownerDataSource());

    protected ResultActions as(GcPrincipal actor, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", TestJwts.user(actor.subject()).bearer()));
    }

    protected ResultActions search(GcPrincipal actor, String query) throws Exception {
        return as(actor, get("/products?" + query));
    }

    protected ResultActions importAs(String authorization, String path, Map<String, Object> body) throws Exception {
        var request = put(path).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mvc.perform(request);
    }

    /** A product code no other test uses. */
    protected static String freshCode() {
        return "TST-" + NEXT_CODE.getAndIncrement();
    }

    protected static Map<String, Object> product(String name, long version) {
        return Map.of("buCode", "CP", "name", name, "kind", "FUNGICIDE", "status", "ACTIVE", "version", version,
                "activeIngredients", List.of(Map.of("name", "Testazole", "concentration", "250 g/L")), "packSizes", List.of("1 L"));
    }

    protected List<String> events(String productId) {
        return owner.sql("SELECT event_type || ' ' || payload::text FROM catalog.outbox WHERE aggregate_id = ? ORDER BY occurred_at")
                .param(productId).query(String.class).list();
    }

    protected static ResultActions problem(ResultActions r, int status, String code) throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value(code));
    }
}
