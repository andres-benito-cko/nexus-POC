package com.checkout.nexus.lesimulator.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemeProfileTest {

    @Test
    void allProfilesHaveRequiredFields() {
        for (SchemeProfile p : SchemeProfile.ALL) {
            assertNotNull(p.schemeName(),             p.schemeName() + ": schemeName null");
            assertNotNull(p.paymentMethodBalances(),  p.schemeName() + ": paymentMethodBalances null");
            assertNotNull(p.paymentMethodCos(),       p.schemeName() + ": paymentMethodCos null");
            assertNotNull(p.defaultCurrency(),        p.schemeName() + ": defaultCurrency null");
            assertNotNull(p.settlementServiceName(),  p.schemeName() + ": settlementServiceName null");
            assertNotNull(p.acquirerCountry(),        p.schemeName() + ": acquirerCountry null");
            assertTrue(p.interchangeFeeRate() >= 0,   p.schemeName() + ": negative IC rate");
            assertTrue(p.schemeFeeRate() >= 0,        p.schemeName() + ": negative scheme rate");
        }
    }

    @Test
    void volumeWeightsSumToOne() {
        double sum = 0;
        for (double w : SchemeProfile.VOLUME_WEIGHTS) {
            sum += w;
        }
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    void volumeWeightsCountMatchesProfileCount() {
        assertEquals(SchemeProfile.ALL.size(), SchemeProfile.VOLUME_WEIGHTS.length);
    }

    @Test
    void sixSchemesAreDefined() {
        assertEquals(6, SchemeProfile.ALL.size());
    }
}
