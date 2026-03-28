package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.le.AmountValue;
import com.checkout.nexus.transformer.model.le.BalancesChangedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolver name: {@code FUNDING_AMOUNT}
 *
 * <p>Returns the holding amount from the first FIAPI (BalancesChangedEvent) action's
 * pending changes, or null when not available.
 */
@Component
public class FundingAmountResolver implements FieldResolver {

    @Override
    public String name() {
        return "FUNDING_AMOUNT";
    }

    @Override
    public Object resolve(LeContext ctx, Map<String, Object> params) {
        BalancesChangedEvent fiapi = ctx.getFiapi();
        if (fiapi == null) {
            return null;
        }
        List<BalancesChangedEvent.Action> actions = fiapi.getActions();
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        BalancesChangedEvent.Action firstAction = actions.get(0);
        if (firstAction.getChanges() == null) {
            return null;
        }
        BalancesChangedEvent.PendingChange pending = firstAction.getChanges().getPending();
        if (pending == null) {
            return null;
        }
        AmountValue holdingAmount = pending.getHoldingAmount();
        return holdingAmount;
    }
}
