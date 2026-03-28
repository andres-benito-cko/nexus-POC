package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.le.AmountValue;
import com.checkout.nexus.transformer.model.le.BalancesChangedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolver name: {@code ROLLING_RESERVE_AMOUNT}
 *
 * <p>Returns the rolling reserve amount from the first FIAPI action, or null when
 * absent or zero.
 */
@Component
public class RollingReserveAmountResolver implements FieldResolver {

    @Override
    public String name() {
        return "ROLLING_RESERVE_AMOUNT";
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
        BalancesChangedEvent.Changes changes = actions.get(0).getChanges();
        if (changes == null) {
            return null;
        }
        AmountValue reserve = changes.getRollingReserve();
        if (reserve == null || reserve.getValue() <= 0) {
            return null;
        }
        return reserve;
    }
}
