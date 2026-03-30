package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import com.checkout.nexus.lesimulator.model.SchemeProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioLoaderTest {

    private final ScenarioLoader loader = new ScenarioLoader();

    // --- Pillar ordering (acquiring scenarios 01-06) ---

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06"})
    void acquiringScenarios_v1HasOnlyGateway(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction v1 = versions.get(0);
        assertEquals(1, v1.getTransactionVersion());
        assertFalse(v1.getGatewayEvents().isEmpty(),              "v1 must have GW");
        assertTrue(v1.getCosEvents().isEmpty(),                   "v1 must not have COS");
        assertTrue(v1.getBalancesChangedEvents().isEmpty(),       "v1 must not have Balances");
        assertTrue(v1.getSchemeSettlementEvents().isEmpty(),      "v1 must not have SD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06"})
    void acquiringScenarios_v2AddsCosBeforeBalances(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction v2 = versions.get(1);
        assertEquals(2, v2.getTransactionVersion());
        assertFalse(v2.getCosEvents().isEmpty(),              "v2 must have COS");
        assertTrue(v2.getBalancesChangedEvents().isEmpty(),   "v2 must not yet have Balances");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06"})
    void acquiringScenarios_v3AddsBalances(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction v3 = versions.get(2);
        assertEquals(3, v3.getTransactionVersion());
        assertFalse(v3.getBalancesChangedEvents().isEmpty(),     "v3 must have Balances");
        assertTrue(v3.getSchemeSettlementEvents().isEmpty(),     "v3 must not yet have SD");
    }

    // --- All scenarios settle ---

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06", "07", "08", "09"})
    void allScenarios_lastVersionHasSD(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        LeLinkedTransaction last = versions.get(versions.size() - 1);
        assertFalse(last.getSchemeSettlementEvents().isEmpty(),
            "Scenario " + scenarioId + " last version must have SD");
    }

    // --- Scheme reflection ---

    @Test
    void scheme_sdMetadataReflectsSchemeProfile() {
        List<LeLinkedTransaction> versions = loader.loadScenario("01", SchemeProfile.CARTE_BANCAIRE);
        LeLinkedTransaction last = versions.get(versions.size() - 1);
        assertEquals("Carte Bancaire",
            last.getSchemeSettlementEvents().get(0).getMetadata().getScheme());
    }

    @Test
    void scheme_cosPaymentMethodReflectsSchemeProfile() {
        List<LeLinkedTransaction> versions = loader.loadScenario("01", SchemeProfile.MASTERCARD);
        // v2 is the COS version
        assertFalse(versions.get(1).getCosEvents().isEmpty());
        assertEquals("mastercard",
            versions.get(1).getCosEvents().get(0).getMetadata().getPaymentMethod());
    }

    @Test
    void scheme_balancesPaymentMethodReflectsSchemeProfile() {
        List<LeLinkedTransaction> versions = loader.loadScenario("01", SchemeProfile.MASTERCARD);
        // v3 is the Balances version
        assertFalse(versions.get(2).getBalancesChangedEvents().isEmpty());
        assertEquals("MASTERCARD",
            versions.get(2).getBalancesChangedEvents().get(0).getMetadata().getPaymentMethod());
    }

    // --- Shared action_id across versions ---

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "04", "05", "06", "07", "08", "09"})
    void allScenarios_versionsShareActionId(String scenarioId) {
        List<LeLinkedTransaction> versions = loader.loadScenario(scenarioId, SchemeProfile.VISA);
        String actionId = versions.get(0).getActionId();
        versions.forEach(v -> assertEquals(actionId, v.getActionId(),
            "Scenario " + scenarioId + " version " + v.getTransactionVersion() + " has wrong actionId"));
    }

    // --- buildRandomCaptureSequence ---

    @Test
    void buildRandomCaptureSequence_produces4VersionsInOrder() {
        List<LeLinkedTransaction> versions = loader.buildRandomCaptureSequence(SchemeProfile.VISA, 100.0, "cli_test");
        assertEquals(4, versions.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, versions.get(i).getTransactionVersion());
        }
    }

    @Test
    void buildRandomCaptureSequence_allVersionsShareActionId() {
        List<LeLinkedTransaction> versions = loader.buildRandomCaptureSequence(SchemeProfile.VISA, 100.0, "cli_test");
        String actionId = versions.get(0).getActionId();
        versions.forEach(v -> assertEquals(actionId, v.getActionId()));
    }

    @Test
    void buildRandomCaptureSequence_v1GwOnly_v4Settled() {
        List<LeLinkedTransaction> versions = loader.buildRandomCaptureSequence(SchemeProfile.VISA, 100.0, "cli_test");
        LeLinkedTransaction v1 = versions.get(0);
        assertTrue(v1.getCosEvents().isEmpty());
        assertTrue(v1.getBalancesChangedEvents().isEmpty());
        assertFalse(versions.get(3).getSchemeSettlementEvents().isEmpty());
    }
}
