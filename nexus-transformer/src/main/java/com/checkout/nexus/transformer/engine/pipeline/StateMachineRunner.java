package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.ConfigHolder;
import com.checkout.nexus.transformer.engine.config.StateConfig;
import com.checkout.nexus.transformer.engine.config.StateMachineConfig;
import com.checkout.nexus.transformer.engine.config.TransitionRule;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs the state machine for a given product type to determine block
 * and transaction statuses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateMachineRunner {

    private final ConfigHolder config;
    private final ExpressionEvaluator evaluator;

    /**
     * Evaluates transitions in order; returns statuses for the first matching state.
     *
     * @param ctx         current LE context
     * @param productType e.g. "ACQUIRING", "PAYOUT", "CASH"
     * @return resolved {@link StateResult}
     */
    public StateResult run(LeContext ctx, String productType) {
        StateMachineConfig sm = config.getStateMachines().get(productType);
        if (sm == null) {
            log.warn("No state machine configured for productType '{}'; defaulting to NOT_LIVE/CAPTURED", productType);
            return new StateResult("NOT_LIVE", "CAPTURED");
        }

        List<TransitionRule> transitions = sm.getTransitions();
        for (TransitionRule rule : transitions) {
            if (rule.isDefaultRule()) {
                return resolveState(sm, rule.effectiveTarget(), productType);
            }
            if (evaluator.evaluateCondition(rule.getWhen(), ctx)) {
                return resolveState(sm, rule.effectiveTarget(), productType);
            }
        }

        log.warn("No transition matched for productType '{}'; using first state", productType);
        return new StateResult("NOT_LIVE", "CAPTURED");
    }

    private StateResult resolveState(StateMachineConfig sm, String stateName, String productType) {
        StateConfig state = sm.getStates().get(stateName);
        if (state == null) {
            log.warn("State '{}' not found in state machine for '{}'; defaulting", stateName, productType);
            return new StateResult("NOT_LIVE", "CAPTURED");
        }
        return new StateResult(state.getBlockStatus(), state.getTransactionStatus());
    }
}
