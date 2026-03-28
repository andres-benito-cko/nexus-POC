package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.le.AmountValue;
import com.checkout.nexus.transformer.model.le.GatewayEvent;
import com.checkout.nexus.transformer.model.le.SchemeSettlementEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resolver name: {@code SETTLEMENT_AMOUNT}
 *
 * <p>Params:
 * <ul>
 *   <li>{@code source_priority} — ordered list of sources to try: {@code SD} (actual) or
 *       {@code GATEWAY} (predicted proxy). COS events carry fee data only and are not a
 *       valid source for settlement amount.</li>
 * </ul>
 *
 * <p>Returns an {@link AmountValue} or null when nothing is found.
 */
@Component
public class SettlementAmountResolver implements FieldResolver {

    @Override
    public String name() {
        return "SETTLEMENT_AMOUNT";
    }

    @Override
    public Object resolve(LeContext ctx, Map<String, Object> params) {
        List<String> priority = sourcePriority(params);

        for (String source : priority) {
            if ("SD".equals(source) && ctx.hasPillar("SD")) {
                AmountValue amount = sdSettlementAmount(ctx);
                if (amount != null) {
                    return amount;
                }
            }
            if ("GATEWAY".equals(source) && ctx.hasPillar("GATEWAY")) {
                AmountValue amount = gatewayAmount(ctx);
                if (amount != null) {
                    return amount;
                }
            }
        }
        return null;
    }

    private AmountValue sdSettlementAmount(LeContext ctx) {
        SchemeSettlementEvent sd = ctx.getSd();
        if (sd == null || sd.getPayload() == null || sd.getPayload().getSettlementAmount() == null) {
            return null;
        }
        return sd.getPayload().getSettlementAmount().getMoney();
    }

    private AmountValue gatewayAmount(LeContext ctx) {
        GatewayEvent gw = ctx.getGateway();
        if (gw == null || gw.getAmount() == null) {
            return null;
        }
        return gw.getAmount();
    }

    @SuppressWarnings("unchecked")
    private List<String> sourcePriority(Map<String, Object> params) {
        if (params != null && params.containsKey("source_priority")) {
            Object val = params.get("source_priority");
            if (val instanceof List<?>) {
                return (List<String>) val;
            }
        }
        return List.of("SD", "GATEWAY");
    }
}
