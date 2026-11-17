package com.growercare.catalog.domain;

import static com.growercare.catalog.support.TestPrincipals.CARE_BR;
import static com.growercare.catalog.support.TestPrincipals.CARE_IN;
import static com.growercare.catalog.support.TestPrincipals.GROWER_IN1;
import static com.growercare.catalog.support.TestPrincipals.RETAILER_IN;
import static com.growercare.catalog.support.TestPrincipals.TM_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.growercare.security.EffectiveGrant;
import com.growercare.security.GcPrincipal;
import com.growercare.security.PiiClearance;
import com.growercare.security.ScopeKind;
import com.growercare.security.UserType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** SPEC-CAT-01 D4 (CAT-E4): who may browse which country's catalog. */
class CountryAccessTest {

    static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    static GcPrincipal staffWith(ScopeKind kind, Set<String> countries, Instant validTo) {
        return new GcPrincipal(900, UUID.randomUUID(), UserType.STAFF, "IN", null, List.of(new EffectiveGrant(1, "X", 50, kind,
                PiiClearance.MASKED, Set.of(), countries, Set.of(), Set.of(), validTo)));
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                argumentSet("CAT-E4 grower: home country", GROWER_IN1, "IN", true),
                argumentSet("CAT-E4 grower: another country", GROWER_IN1, "BR", false),
                argumentSet("CAT-E4 retailer: home country only", RETAILER_IN, "BR", false),
                argumentSet("CAT-E4 territory manager: home country", TM_IN, "IN", true),
                argumentSet("CAT-E4 care agent BR: not IN", CARE_BR, "IN", false),
                argumentSet("CAT-E4 care agent IN: IN", CARE_IN, "IN", true),
                argumentSet("CAT-E4 staff with a BR country grant", staffWith(ScopeKind.COUNTRY, Set.of("BR"), null), "BR", true),
                argumentSet("CAT-E4 staff with an expired BR grant", staffWith(ScopeKind.COUNTRY, Set.of("BR"), NOW.minusSeconds(1)), "BR", false),
                argumentSet("CAT-E4 global staff", staffWith(ScopeKind.GLOBAL, Set.of(), null), "BR", true),
                argumentSet("CAT-E4 no country", CARE_IN, null, false));
    }

    @ParameterizedTest
    @MethodSource("rows")
    void mayBrowse(GcPrincipal principal, String country, boolean expected) {
        assertThat(CountryAccess.mayBrowse(principal, country, NOW)).isEqualTo(expected);
    }
}
