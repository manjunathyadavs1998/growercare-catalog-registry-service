package com.growercare.catalog.adapters.out.persistence;

import com.growercare.catalog.application.port.CatalogRepository;
import com.growercare.catalog.domain.Catalog.Batch;
import com.growercare.catalog.domain.Catalog.BatchStatus;
import com.growercare.catalog.domain.Catalog.Ingredient;
import com.growercare.catalog.domain.Catalog.Kind;
import com.growercare.catalog.domain.Catalog.Listing;
import com.growercare.catalog.domain.Catalog.Product;
import com.growercare.catalog.domain.Catalog.ProductStatus;
import com.growercare.catalog.domain.Catalog.Registration;
import com.growercare.catalog.domain.Catalog.RegistrationStatus;
import com.growercare.common.sql.NamedSqlRegistry;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link CatalogRepository} with named SQL ({@code sql/postgres/catalog.sql}). */
@Repository
class JdbcCatalogRepository implements CatalogRepository {

    private final JdbcClient jdbc;
    private final NamedSqlRegistry sql;

    JdbcCatalogRepository(JdbcClient jdbc, NamedSqlRegistry sql) {
        this.jdbc = jdbc;
        this.sql = sql;
    }

    private JdbcClient.StatementSpec statement(String name) {
        return jdbc.sql(sql.get("catalog." + name));
    }

    @Override
    public boolean countryExists(String countryCode) {
        return Boolean.TRUE.equals(statement("countryExists").param("country", countryCode).query(Boolean.class).single());
    }

    @Override
    public boolean businessUnitExists(String buCode) {
        return Boolean.TRUE.equals(statement("buExists").param("bu", buCode).query(Boolean.class).single());
    }

    @Override
    public List<Listing> search(String country, String q, Kind kind, After after, int limit, LocalDate today) {
        String like = q == null ? null : escapeLike(q);
        return statement("search")
                .param("country", country)
                .param("today", Date.valueOf(today))
                .param("namePattern", like == null ? null : "%" + like.toLowerCase(Locale.ROOT) + "%", Types.VARCHAR)
                .param("codePattern", like == null ? null : like.toUpperCase(Locale.ROOT) + "%", Types.VARCHAR)
                .param("kind", kind == null ? null : kind.name(), Types.VARCHAR)
                .param("afterName", after == null ? null : after.name(), Types.VARCHAR)
                .param("afterId", after == null ? null : after.productId(), Types.BIGINT)
                .param("limit", limit)
                .query((rs, n) -> {
                    var product = productRow(rs);
                    var registration = new Registration(rs.getString("country_code"), product.productId(), rs.getString("registration_no"),
                            RegistrationStatus.valueOf(rs.getString("registration_status")), localDate(rs, "valid_until"),
                            rs.getLong("registration_version"));
                    return new Listing(product, registration);
                })
                .list();
    }

    @Override
    public Optional<Product> product(long productId) {
        return statement("product").param("productId", productId).query((rs, n) -> productRow(rs)).optional()
                .map(this::withLists);
    }

    @Override
    public List<Registration> registrations(long productId) {
        return statement("registrations").param("productId", productId).query(JdbcCatalogRepository::registrationRow).list();
    }

    @Override
    public Optional<Registration> registration(String country, long productId) {
        return statement("registration").param("country", country).param("productId", productId)
                .query(JdbcCatalogRepository::registrationRow).optional();
    }

    @Override
    public Optional<Batch> batch(long productId, String batchNo) {
        return statement("batch").param("productId", productId).param("batchNo", batchNo).query(JdbcCatalogRepository::batchRow).optional();
    }

    @Override
    public Optional<Product> lockProduct(String productCode) {
        return statement("lockProduct").param("code", productCode).query((rs, n) -> productRow(rs)).optional().map(this::withLists);
    }

    @Override
    public Optional<Long> insertProduct(String code, String bu, String name, Kind kind, String status, long version) {
        return statement("insertProduct").param("code", code).param("bu", bu).param("name", name).param("kind", kind.name())
                .param("status", status).param("version", version).query(Long.class).optional();
    }

    @Override
    public void updateProduct(long productId, String bu, String name, Kind kind, String status, long version) {
        statement("updateProduct").param("productId", productId).param("bu", bu).param("name", name).param("kind", kind.name())
                .param("status", status).param("version", version).update();
    }

    @Override
    public void replaceIngredients(long productId, List<Ingredient> ingredients) {
        statement("deleteIngredients").param("productId", productId).update();
        for (int i = 0; i < ingredients.size(); i++) {
            statement("insertIngredient").param("productId", productId).param("position", i + 1)
                    .param("name", ingredients.get(i).name()).param("concentration", ingredients.get(i).concentration()).update();
        }
    }

    @Override
    public void replacePacks(long productId, List<String> packSizes) {
        statement("deletePacks").param("productId", productId).update();
        for (int i = 0; i < packSizes.size(); i++) {
            statement("insertPack").param("productId", productId).param("position", i + 1).param("packSize", packSizes.get(i)).update();
        }
    }

    @Override
    public Optional<Registration> lockRegistration(String country, long productId) {
        return statement("lockRegistration").param("country", country).param("productId", productId)
                .query(JdbcCatalogRepository::registrationRow).optional();
    }

    @Override
    public void saveRegistration(Registration r) {
        statement("saveRegistration").param("country", r.countryCode()).param("productId", r.productId())
                .param("registrationNo", r.registrationNo()).param("status", r.status().name())
                .param("validUntil", r.validUntil() == null ? null : Date.valueOf(r.validUntil()), Types.DATE)
                .param("version", r.version()).update();
    }

    @Override
    public Optional<Batch> lockBatch(long productId, String batchNo) {
        return statement("lockBatch").param("productId", productId).param("batchNo", batchNo).query(JdbcCatalogRepository::batchRow).optional();
    }

    @Override
    public void saveBatch(Batch b) {
        statement("saveBatch").param("productId", b.productId()).param("batchNo", b.batchNo())
                .param("mfgDate", Date.valueOf(b.mfgDate())).param("expiryDate", Date.valueOf(b.expiryDate()))
                .param("plantCode", b.plantCode()).param("status", b.status().name()).param("version", b.version()).update();
    }

    private Product withLists(Product p) {
        var ingredients = statement("ingredients").param("productId", p.productId())
                .query((rs, n) -> new Ingredient(rs.getString("name"), rs.getString("concentration"))).list();
        var packs = statement("packs").param("productId", p.productId()).query(String.class).list();
        return new Product(p.productId(), p.productCode(), p.buCode(), p.name(), p.kind(), p.status(), p.version(), ingredients, packs);
    }

    private static Product productRow(ResultSet rs) throws SQLException {
        return new Product(rs.getLong("product_id"), rs.getString("product_code"), rs.getString("bu_code"), rs.getString("name"),
                Kind.valueOf(rs.getString("product_kind")), ProductStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                List.of(), List.of());
    }

    private static Registration registrationRow(ResultSet rs, int n) throws SQLException {
        return new Registration(rs.getString("country_code"), rs.getLong("product_id"), rs.getString("registration_no"),
                RegistrationStatus.valueOf(rs.getString("status")), localDate(rs, "valid_until"), rs.getLong("version"));
    }

    private static Batch batchRow(ResultSet rs, int n) throws SQLException {
        return new Batch(rs.getLong("product_id"), rs.getString("batch_no"), localDate(rs, "mfg_date"), localDate(rs, "expiry_date"),
                rs.getString("plant_code"), BatchStatus.valueOf(rs.getString("status")), rs.getLong("version"));
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        var date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    /** LIKE wildcards in user input are literal. */
    private static String escapeLike(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
