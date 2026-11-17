package com.growercare.catalog.domain;

import com.growercare.security.GcPrincipal;
import com.growercare.security.ScopeKind;
import com.growercare.security.UserType;
import java.time.Instant;

/**
 * SPEC-CAT-01 D4: which country's catalog a user may browse. Growers and partners: their home country. Staff: their
 * home country (where their territory or assigned grants lie) plus every country a current grant names; a GLOBAL grant
 * covers all countries. Anything else is answered as "unknown country" (404).
 */
public final class CountryAccess {

    private CountryAccess() { }

    public static boolean mayBrowse(GcPrincipal principal, String country, Instant now) {
        if (country == null) {
            return false;
        }
        if (country.equals(principal.homeCountry())) {
            return true;
        }
        if (principal.userType() == UserType.GROWER || principal.userType() == UserType.PARTNER) {
            return false;
        }
        return principal.grants().stream()
                .filter(g -> g.validTo() == null || g.validTo().isAfter(now))
                .anyMatch(g -> g.scopeKind() == ScopeKind.GLOBAL || g.countries().contains(country));
    }
}
