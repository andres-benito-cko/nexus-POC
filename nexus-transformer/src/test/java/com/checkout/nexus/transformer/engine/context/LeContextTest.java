package com.checkout.nexus.transformer.engine.context;

import com.checkout.nexus.transformer.model.le.BalancesChangedEvent;
import com.checkout.nexus.transformer.model.le.CashEvent;
import com.checkout.nexus.transformer.model.le.CosEvent;
import com.checkout.nexus.transformer.model.le.GatewayEvent;
import com.checkout.nexus.transformer.model.le.LeLinkedTransaction;
import com.checkout.nexus.transformer.model.le.SchemeSettlementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeContextTest {

    private LeLinkedTransaction tx;

    @BeforeEach
    void setUp() {
        tx = new LeLinkedTransaction();
        tx.setId("tx-001");
        tx.setActionRootId("root-001");
        tx.setActionId("action-001");
        tx.setTransactionVersion(1);
    }

    // ------------------------------------------------------------------ hasPillar

    @Test
    @DisplayName("hasPillar returns true for GATEWAY when gatewayEvents is non-empty")
    void hasPillar_gateway_true() {
        tx.setGatewayEvents(List.of(new GatewayEvent()));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("GATEWAY")).isTrue();
    }

    @Test
    @DisplayName("hasPillar returns false for GATEWAY when gatewayEvents is empty")
    void hasPillar_gateway_false() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("GATEWAY")).isFalse();
    }

    @Test
    @DisplayName("hasPillar returns true for FIAPI when balancesChangedEvents is non-empty")
    void hasPillar_fiapi_true() {
        tx.setBalancesChangedEvents(List.of(new BalancesChangedEvent()));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("FIAPI")).isTrue();
    }

    @Test
    @DisplayName("hasPillar returns false for FIAPI when balancesChangedEvents is empty")
    void hasPillar_fiapi_false() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("FIAPI")).isFalse();
    }

    @Test
    @DisplayName("hasPillar returns true for COS when cosEvents is non-empty")
    void hasPillar_cos_true() {
        tx.setCosEvents(List.of(new CosEvent()));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("COS")).isTrue();
    }

    @Test
    @DisplayName("hasPillar returns false for COS when cosEvents is empty")
    void hasPillar_cos_false() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("COS")).isFalse();
    }

    @Test
    @DisplayName("hasPillar returns true for SD when schemeSettlementEvents is non-empty")
    void hasPillar_sd_true() {
        tx.setSchemeSettlementEvents(List.of(new SchemeSettlementEvent()));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("SD")).isTrue();
    }

    @Test
    @DisplayName("hasPillar returns false for SD when schemeSettlementEvents is empty")
    void hasPillar_sd_false() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("SD")).isFalse();
    }

    @Test
    @DisplayName("hasPillar returns true for CASH when cashEvents is non-empty")
    void hasPillar_cash_true() {
        tx.setCashEvents(List.of(new CashEvent()));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("CASH")).isTrue();
    }

    @Test
    @DisplayName("hasPillar returns false for CASH when cashEvents is empty")
    void hasPillar_cash_false() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("CASH")).isFalse();
    }

    @Test
    @DisplayName("hasPillar returns false for unknown pillar name")
    void hasPillar_unknown_false() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.hasPillar("UNKNOWN")).isFalse();
    }

    // ------------------------------------------------------------------ typed accessors

    @Test
    @DisplayName("getGateway returns first GatewayEvent or null when absent")
    void getGateway_returnsFirstOrNull() {
        LeContext emptyCtx = new LeContext(tx);
        assertThat(emptyCtx.getGateway()).isNull();

        GatewayEvent gw = new GatewayEvent();
        gw.setEventId("gw-1");
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.getGateway()).isSameAs(gw);
        assertThat(ctx.getGateway().getEventId()).isEqualTo("gw-1");
    }

    @Test
    @DisplayName("getFiapi returns first BalancesChangedEvent or null when absent")
    void getFiapi_returnsFirstOrNull() {
        LeContext emptyCtx = new LeContext(tx);
        assertThat(emptyCtx.getFiapi()).isNull();

        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.getFiapi()).isSameAs(fiapi);
    }

    @Test
    @DisplayName("getSd returns first SchemeSettlementEvent or null when absent")
    void getSd_returnsFirstOrNull() {
        LeContext emptyCtx = new LeContext(tx);
        assertThat(emptyCtx.getSd()).isNull();

        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.getSd()).isSameAs(sd);
    }

    @Test
    @DisplayName("getCash returns first CashEvent or null when absent")
    void getCash_returnsFirstOrNull() {
        LeContext emptyCtx = new LeContext(tx);
        assertThat(emptyCtx.getCash()).isNull();

        CashEvent cash = new CashEvent();
        tx.setCashEvents(List.of(cash));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.getCash()).isSameAs(cash);
    }

    @Test
    @DisplayName("getAllCos returns all COS events")
    void getAllCos_returnsAllEvents() {
        LeContext emptyCtx = new LeContext(tx);
        assertThat(emptyCtx.getAllCos()).isEmpty();

        CosEvent cos1 = new CosEvent();
        CosEvent cos2 = new CosEvent();
        CosEvent cos3 = new CosEvent();
        tx.setCosEvents(List.of(cos1, cos2, cos3));
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.getAllCos()).hasSize(3);
        assertThat(ctx.getAllCos()).containsExactly(cos1, cos2, cos3);
    }

    @Test
    @DisplayName("getRaw returns the original LeLinkedTransaction")
    void getRaw_returnsOriginalTx() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.getRaw()).isSameAs(tx);
    }

    // ------------------------------------------------------------------ get(dotPath)

    @Test
    @DisplayName("get() resolves a single-level SD path")
    void get_singleLevel_sd() {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        SchemeSettlementEvent.Metadata meta = new SchemeSettlementEvent.Metadata();
        meta.setScheme("VISA");
        // Use reflection-accessible setter via Lombok; set via the inner class
        tx.setSchemeSettlementEvents(List.of(sd));

        // SD.metadata is null still — should return null
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.get("SD.metadata")).isNull();
    }

    @Test
    @DisplayName("get() resolves nested SD.metadata.scheme")
    void get_nestedPath_sd_scheme() throws Exception {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        SchemeSettlementEvent.Metadata meta = new SchemeSettlementEvent.Metadata();
        meta.setScheme("VISA");

        // Set metadata via reflection since it's a private field in the inner class
        java.lang.reflect.Field metaField = SchemeSettlementEvent.class.getDeclaredField("metadata");
        metaField.setAccessible(true);
        metaField.set(sd, meta);

        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        assertThat(ctx.get("SD.metadata.scheme")).isEqualTo("VISA");
    }

    @Test
    @DisplayName("get() resolves GATEWAY.eventId")
    void get_gateway_eventId() throws Exception {
        GatewayEvent gw = new GatewayEvent();

        java.lang.reflect.Field f = GatewayEvent.class.getDeclaredField("eventId");
        f.setAccessible(true);
        f.set(gw, "gw-evt-42");

        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        assertThat(ctx.get("GATEWAY.eventId")).isEqualTo("gw-evt-42");
    }

    @Test
    @DisplayName("get() resolves COS.payload.feeType using first COS event")
    void get_cos_feeType() throws Exception {
        CosEvent cos = new CosEvent();
        CosEvent.Payload payload = new CosEvent.Payload();

        java.lang.reflect.Field ftField = CosEvent.Payload.class.getDeclaredField("feeType");
        ftField.setAccessible(true);
        ftField.set(payload, "SCHEME_FEE");

        java.lang.reflect.Field pField = CosEvent.class.getDeclaredField("payload");
        pField.setAccessible(true);
        pField.set(cos, payload);

        tx.setCosEvents(List.of(cos));
        LeContext ctx = new LeContext(tx);

        assertThat(ctx.get("COS.payload.feeType")).isEqualTo("SCHEME_FEE");
    }

    @Test
    @DisplayName("get() returns null when path doesn't exist on a present pillar")
    void get_returnsNull_nonExistentField() {
        GatewayEvent gw = new GatewayEvent();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        assertThat(ctx.get("GATEWAY.nonExistentField")).isNull();
    }

    @Test
    @DisplayName("get() returns null when pillar has no events")
    void get_returnsNull_pillarAbsent() {
        LeContext ctx = new LeContext(tx);
        assertThat(ctx.get("SD.metadata.scheme")).isNull();
    }

    @Test
    @DisplayName("get() returns null for null intermediate segment")
    void get_returnsNull_nullIntermediateSegment() {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        // metadata is not set, remains null
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        // SD.metadata is null, so SD.metadata.scheme should return null safely
        assertThat(ctx.get("SD.metadata.scheme")).isNull();
    }

    @Test
    @DisplayName("get() resolves FIAPI.metadata.entityId")
    void get_fiapi_metadata_entityId() throws Exception {
        BalancesChangedEvent fiapi = new BalancesChangedEvent();
        BalancesChangedEvent.Metadata meta = new BalancesChangedEvent.Metadata();

        java.lang.reflect.Field eidField = BalancesChangedEvent.Metadata.class.getDeclaredField("entityId");
        eidField.setAccessible(true);
        eidField.set(meta, "entity-xyz");

        java.lang.reflect.Field mField = BalancesChangedEvent.class.getDeclaredField("metadata");
        mField.setAccessible(true);
        mField.set(fiapi, meta);

        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        assertThat(ctx.get("FIAPI.metadata.entityId")).isEqualTo("entity-xyz");
    }

    @Test
    @DisplayName("get() resolves CASH.standardMetadata.scheme")
    void get_cash_scheme() throws Exception {
        CashEvent cash = new CashEvent();
        CashEvent.StandardMetadata sm = new CashEvent.StandardMetadata();

        java.lang.reflect.Field schemeField = CashEvent.StandardMetadata.class.getDeclaredField("scheme");
        schemeField.setAccessible(true);
        schemeField.set(sm, "MASTERCARD");

        java.lang.reflect.Field smField = CashEvent.class.getDeclaredField("standardMetadata");
        smField.setAccessible(true);
        smField.set(cash, sm);

        tx.setCashEvents(List.of(cash));
        LeContext ctx = new LeContext(tx);

        assertThat(ctx.get("CASH.standardMetadata.scheme")).isEqualTo("MASTERCARD");
    }
}
