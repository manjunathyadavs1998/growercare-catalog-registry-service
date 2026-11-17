package com.growercare.catalog.support;

import com.growercare.security.EffectiveGrant;
import com.growercare.security.GcPrincipal;
import com.growercare.security.PiiClearance;
import com.growercare.security.ScopeKind;
import com.growercare.security.UserType;
import com.growercare.testsupport.TestJwts;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The dev-seed users as IAM resolves them (grants' scopes only: the catalog needs no permission, SPEC-CAT-01 D4). */
public final class TestPrincipals {

    private TestPrincipals() { }

    public static final GcPrincipal GROWER_IN1 = grower(1, TestJwts.GROWER_IN1, "IN");
    public static final GcPrincipal GROWER_BR1 = grower(3, TestJwts.GROWER_BR1, "BR");
    public static final GcPrincipal CARE_IN = staff(101, TestJwts.CARE_IN, "IN", ScopeKind.COUNTRY, Set.of("IN"));
    public static final GcPrincipal TM_IN = staff(103, TestJwts.TM_IN, "IN", ScopeKind.TERRITORY, Set.of());
    public static final GcPrincipal CARE_BR = staff(105, TestJwts.CARE_BR, "BR", ScopeKind.COUNTRY, Set.of("BR"));
    public static final GcPrincipal RETAILER_IN = new GcPrincipal(201, TestJwts.RETAILER_IN, UserType.PARTNER, "IN", 501L,
            List.of(grant("RETAILER_USER", ScopeKind.ORG, Set.of("IN"))));

    public static List<GcPrincipal> all() {
        return List.of(GROWER_IN1, GROWER_BR1, CARE_IN, TM_IN, CARE_BR, RETAILER_IN);
    }

    public static Optional<GcPrincipal> resolve(UUID subject) {
        return all().stream().filter(p -> p.subject().equals(subject)).findFirst();
    }

    static EffectiveGrant grant(String role, ScopeKind kind, Set<String> countries) {
        return new EffectiveGrant(1, role, 10, kind, PiiClearance.MASKED, Set.of(), countries, Set.of(), Set.of(), null);
    }

    private static GcPrincipal grower(long id, UUID subject, String country) {
        return new GcPrincipal(id, subject, UserType.GROWER, country, null, List.of(grant("GROWER", ScopeKind.OWN, Set.of(country))));
    }

    private static GcPrincipal staff(long id, UUID subject, String country, ScopeKind kind, Set<String> countries) {
        return new GcPrincipal(id, subject, UserType.STAFF, country, null, List.of(grant("STAFF", kind, countries)));
    }
}
