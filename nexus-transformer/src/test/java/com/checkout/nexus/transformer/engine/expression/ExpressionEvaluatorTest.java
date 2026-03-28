package com.checkout.nexus.transformer.engine.expression;

import com.checkout.nexus.transformer.engine.config.FieldMapping;
import com.checkout.nexus.transformer.engine.config.WhenCondition;
import com.checkout.nexus.transformer.engine.config.WhenExpression;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.resolver.ResolverRegistry;
import com.checkout.nexus.transformer.model.le.GatewayEvent;
import com.checkout.nexus.transformer.model.le.LeLinkedTransaction;
import com.checkout.nexus.transformer.model.le.SchemeSettlementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExpressionEvaluatorTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ResolverRegistry registry = mock(ResolverRegistry.class);
        evaluator = new ExpressionEvaluator(registry);
    }

    // ------------------------------------------------------------------ $field resolution

    @Test
    @DisplayName("FieldMapping: resolves first non-null path — GATEWAY.eventId")
    void fieldMapping_resolvesFirstNonNull() throws Exception {
        GatewayEvent gw = new GatewayEvent();
        Field f = GatewayEvent.class.getDeclaredField("eventId");
        f.setAccessible(true);
        f.set(gw, "gw-evt-99");

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setGatewayEvents(List.of(gw));
        LeContext ctx = new LeContext(tx);

        FieldMapping mapping = new FieldMapping();
        mapping.setFieldPaths(List.of("GATEWAY.eventId"));

        Object result = evaluator.evaluate(mapping, ctx);
        assertThat(result).isEqualTo("gw-evt-99");
    }

    @Test
    @DisplayName("FieldMapping: skips null paths and returns fallback string")
    void fieldMapping_fallback_string() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        FieldMapping mapping = new FieldMapping();
        mapping.setFieldPaths(List.of("GATEWAY.eventId", "SD.metadata.scheme"));
        mapping.setFallback("DEFAULT_VALUE");

        Object result = evaluator.evaluate(mapping, ctx);
        assertThat(result).isEqualTo("DEFAULT_VALUE");
    }

    @Test
    @DisplayName("FieldMapping: $now() fallback returns non-null ISO timestamp")
    void fieldMapping_fallback_now() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        FieldMapping mapping = new FieldMapping();
        mapping.setFieldPaths(List.of("GATEWAY.processedOn"));
        mapping.setFallback("$now()");

        Object result = evaluator.evaluate(mapping, ctx);
        assertThat(result).isNotNull().isInstanceOf(String.class);
        assertThat((String) result).isNotBlank();
    }

    @Test
    @DisplayName("FieldMapping: returns null when all paths null and no fallback")
    void fieldMapping_noFallback_returnsNull() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        FieldMapping mapping = new FieldMapping();
        mapping.setFieldPaths(List.of("GATEWAY.eventId"));

        Object result = evaluator.evaluate(mapping, ctx);
        assertThat(result).isNull();
    }

    // ------------------------------------------------------------------ String literal

    @Test
    @DisplayName("String literal evaluates to itself")
    void stringLiteral_returnsItself() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        Object result = evaluator.evaluate("ACTUAL", ctx);
        assertThat(result).isEqualTo("ACTUAL");
    }

    // ------------------------------------------------------------------ WhenExpression

    @Test
    @DisplayName("WhenExpression: SD present → condition matches → returns then value")
    void whenExpression_sdPresent_returnsActual() throws Exception {
        SchemeSettlementEvent sd = new SchemeSettlementEvent();
        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setSchemeSettlementEvents(List.of(sd));
        LeContext ctx = new LeContext(tx);

        WhenCondition cond = new WhenCondition();
        cond.setIfExpr("pillars.contains('SD')");
        cond.setThenResult("ACTUAL");

        WhenCondition elseC = new WhenCondition();
        elseC.setElseResult("PREDICTED");

        WhenExpression expr = new WhenExpression();
        expr.setConditions(List.of(cond, elseC));

        Object result = evaluator.evaluate(expr, ctx);
        assertThat(result).isEqualTo("ACTUAL");
    }

    @Test
    @DisplayName("WhenExpression: SD absent → falls to else branch → returns PREDICTED")
    void whenExpression_sdAbsent_returnsPredicted() {
        LeLinkedTransaction tx = new LeLinkedTransaction();
        LeContext ctx = new LeContext(tx);

        WhenCondition cond = new WhenCondition();
        cond.setIfExpr("pillars.contains('SD')");
        cond.setThenResult("ACTUAL");

        WhenCondition elseC = new WhenCondition();
        elseC.setElseResult("PREDICTED");

        WhenExpression expr = new WhenExpression();
        expr.setConditions(List.of(cond, elseC));

        Object result = evaluator.evaluate(expr, ctx);
        assertThat(result).isEqualTo("PREDICTED");
    }

    @Test
    @DisplayName("WhenExpression: SpEL with ctx.get() — FIAPI action type check")
    void whenExpression_spelWithCtxGet() throws Exception {
        com.checkout.nexus.transformer.model.le.BalancesChangedEvent fiapi =
                new com.checkout.nexus.transformer.model.le.BalancesChangedEvent();
        com.checkout.nexus.transformer.model.le.BalancesChangedEvent.Metadata meta =
                new com.checkout.nexus.transformer.model.le.BalancesChangedEvent.Metadata();
        Field atField = com.checkout.nexus.transformer.model.le.BalancesChangedEvent.Metadata.class.getDeclaredField("actionType");
        atField.setAccessible(true);
        atField.set(meta, "CARD_PAYOUT");
        Field mField = com.checkout.nexus.transformer.model.le.BalancesChangedEvent.class.getDeclaredField("metadata");
        mField.setAccessible(true);
        mField.set(fiapi, meta);

        LeLinkedTransaction tx = new LeLinkedTransaction();
        tx.setBalancesChangedEvents(List.of(fiapi));
        LeContext ctx = new LeContext(tx);

        WhenCondition cond = new WhenCondition();
        cond.setIfExpr("ctx.get('FIAPI.metadata.actionType') != null && ctx.get('FIAPI.metadata.actionType').contains('CARD_PAYOUT')");
        cond.setThenResult("PAYOUT_RESULT");

        WhenCondition elseC = new WhenCondition();
        elseC.setElseResult("OTHER");

        WhenExpression expr = new WhenExpression();
        expr.setConditions(List.of(cond, elseC));

        Object result = evaluator.evaluate(expr, ctx);
        assertThat(result).isEqualTo("PAYOUT_RESULT");
    }
}
