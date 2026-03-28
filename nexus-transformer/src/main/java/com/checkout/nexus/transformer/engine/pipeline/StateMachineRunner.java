package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
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
 * Runs the state machine for a given trade family to determine transaction
 * and trade statuses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateMachineRunner {

    private final NexusEngineConfig config;
    private final ExpressionEvaluator evaluator;

    /**
     * Evaluates transitions in order; returns statuses for the first matching state.
     *
     * @param ctx         current LE context
     * @param tradeFamily e.g. "ACQUIRING", "PAYOUT", "CASH"
     * @return resolved {@link StateResult}
     */
    public StateResult run(LeContext ctx, String tradeFamily) {
        StateMachineConfig sm = config.getStateMachines().get(tradeFamily);
        if (sm == null) {
            log.warn("No state machine configured for tradeFamily '{}'; defaulting to NOT_LIVE/CAPTURED", tradeFamily);
            return new StateResult("NOT_LIVE", "CAPTURED");
        }

        List<TransitionRule> transitions = sm.getTransitions();
        for (TransitionRule rule : transitions) {
            if (rule.isDefaultRule()) {
                return resolveState(sm, rule.effectiveTarget(), tradeFamily);
            }
            if (evaluator.evaluateCondition(rule.getWhen(), ctx)) {
                return resolveState(sm, rule.effectiveTarget(), tradeFamily);
            }
        }

        log.warn("No transition matched for tradeFamily '{}'; using first state", tradeFamily);
        return new StateResult("NOT_LIVE", "CAPTURED");
    }

    private StateResult resolveState(StateMachineConfig sm, String stateName, String tradeFamily) {
        StateConfig state = sm.getStates().get(stateName);
        if (state == null) {
            log.warn("State '{}' not found in state machine for '{}'; defaulting", stateName, tradeFamily);
            return new StateResult("NOT_LIVE", "CAPTURED");
        }
        return new StateResult(state.getTransactionStatus(), state.getTradeStatus());
    }
}
