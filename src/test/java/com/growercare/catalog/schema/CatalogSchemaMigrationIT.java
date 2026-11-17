package com.growercare.catalog.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.growercare.catalog.support.CatalogApiTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** SPEC-CAT-01 R30–R31 (CAT-E30, E31): schema, least privilege, CDC, dev seed. */
class CatalogSchemaMigrationIT extends CatalogApiTestBase {

    final JdbcClient app = JdbcClient.create(DB.appDataSource());

    static Stream<Arguments> denied() {
        return Stream.of(
                argumentSet("CAT-E30 delete products", "DELETE FROM catalog.product"),
                argumentSet("CAT-E30 delete registrations", "DELETE FROM catalog.registration"),
                argumentSet("CAT-E30 delete batches", "DELETE FROM catalog.batch"),
                argumentSet("CAT-E30 change countries", "INSERT INTO catalog.country VALUES ('ZZ', 'x')"),
                argumentSet("CAT-E30 rewrite events", "UPDATE catalog.outbox SET event_type = 'x'"),
                argumentSet("CAT-E30 DDL", "CREATE TABLE catalog.evil (id int)"));
    }

    @ParameterizedTest
    @MethodSource("denied")
    void appRoleIsDenied(String sql) {
        assertThatThrownBy(() -> app.sql(sql).update())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void CAT_R30_theCdcRoleReadsTheOutboxOnly() {
        var cdc = JdbcClient.create(DB.cdcDataSource());
        cdc.sql("SELECT count(*) FROM catalog.outbox").query(Long.class).single();
        assertThatThrownBy(() -> cdc.sql("SELECT count(*) FROM catalog.product").query(Long.class).single())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThat(owner.sql("SELECT string_agg(tablename, ',' ORDER BY tablename) FROM pg_publication_tables WHERE pubname = 'gc_outbox'")
                .query(String.class).single()).isEqualTo("cdc_heartbeat,outbox");
    }

    @ParameterizedTest(name = "CAT-E31 {0} {1}")
    @CsvSource(textBlock = """
            9001, AZX-325SC, CP,    FUNGICIDE,   'BR:REGISTERED,IN:REGISTERED'
            9002, LMB-50CS,  CP,    INSECTICIDE, 'IN:REGISTERED'
            9003, HYC-6668,  SEEDS, SEED,        'IN:REGISTERED'
            9004, MCZ-75WP,  CP,    FUNGICIDE,   'IN:WITHDRAWN'
            """)
    void seedProducts(long id, String code, String bu, String kind, String registrations) {
        var row = owner.sql("SELECT product_code, bu_code, product_kind FROM catalog.product WHERE product_id = ?").param(id).query().singleRow();
        assertThat(row).containsEntry("product_code", code).containsEntry("bu_code", bu).containsEntry("product_kind", kind);
        assertThat(owner.sql("SELECT string_agg(country_code || ':' || status, ',' ORDER BY country_code) FROM catalog.registration WHERE product_id = ?")
                .param(id).query(String.class).single()).isEqualTo(registrations);
    }
}
