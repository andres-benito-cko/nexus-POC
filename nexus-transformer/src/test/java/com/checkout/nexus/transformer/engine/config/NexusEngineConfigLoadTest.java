package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class NexusEngineConfigLoadTest {

    private static NexusEngineConfig config;

    @BeforeAll
    static void loadConfig() throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = NexusEngineConfigLoadTest.class.getClassLoader()
                .getResourceAsStream("nexus-engine-config.yaml")) {
            assertThat(in).as("nexus-engine-config.yaml must be on classpath").isNotNull();
            config = yamlMapper.readValue(in, NexusEngineConfig.class);
        }
    }

    @Test
    @DisplayName("Config loads without errors and has a version")
    void configLoads_hasVersion() {
        assertThat(config).isNotNull();
        assertThat(config.getVersion()).isNotBlank();
    }

    @Test
    @DisplayName("classification.family has at least 3 rules")
    void classification_family_hasAtLeast3Rules() {
        assertThat(config.getClassification()).isNotNull();
        assertThat(config.getClassification().getFamily())
                .as("family rules")
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("stateMachines contains ACQUIRING with states")
    void stateMachines_containsAcquiring() {
        assertThat(config.getStateMachines()).containsKey("ACQUIRING");
        StateMachineConfig acquiring = config.getStateMachines().get("ACQUIRING");
        assertThat(acquiring.getStates()).isNotEmpty();
        assertThat(acquiring.getTransitions()).isNotEmpty();
    }

    @Test
    @DisplayName("stateMachines contains PAYOUT, TOPUP and CASH")
    void stateMachines_containsAllFamilies() {
        assertThat(config.getStateMachines()).containsKeys("PAYOUT", "TOPUP", "CASH");
    }

    @Test
    @DisplayName("trades map contains ACQUIRING with CAPTURE/REFUND/CHARGEBACK/AUTH entries")
    void trades_containsAcquiringTypes() {
        assertThat(config.getTrades()).containsKey("ACQUIRING");
        assertThat(config.getTrades().get("ACQUIRING")).containsKeys("CAPTURE", "REFUND", "CHARGEBACK", "AUTH");
    }

    @Test
    @DisplayName("fieldMappings contains entity_id and cko_entity_id")
    void fieldMappings_hasExpectedKeys() {
        assertThat(config.getFieldMappings()).containsKeys("entity_id", "cko_entity_id");
    }

    @Test
    @DisplayName("feeTypeMappings contains sd and cos entries")
    void feeTypeMappings_hasSdAndCos() {
        assertThat(config.getFeeTypeMappings()).containsKeys("sd", "cos", "fiapi");
    }

    @Test
    @DisplayName("ACQUIRING.GATEWAY_ONLY state has NOT_LIVE transaction status")
    void acquiringGatewayOnlyState_isNotLive() {
        StateMachineConfig acquiring = config.getStateMachines().get("ACQUIRING");
        StateConfig gwOnly = acquiring.getStates().get("GATEWAY_ONLY");
        assertThat(gwOnly).isNotNull();
        assertThat(gwOnly.getTransactionStatus()).isEqualTo("NOT_LIVE");
    }

    @Test
    @DisplayName("ACQUIRING.SETTLED state has LIVE transaction status and SETTLED trade status")
    void acquiringSettledState_isLiveAndSettled() {
        StateMachineConfig acquiring = config.getStateMachines().get("ACQUIRING");
        StateConfig settled = acquiring.getStates().get("SETTLED");
        assertThat(settled).isNotNull();
        assertThat(settled.getTransactionStatus()).isEqualTo("LIVE");
        assertThat(settled.getTradeStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("type classification has priority list")
    void typeClassification_hasPriorityList() {
        assertThat(config.getClassification().getType()).isNotNull();
        assertThat(config.getClassification().getType().getPriority()).isNotEmpty();
    }
}
