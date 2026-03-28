package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.le.AmountValue;
import com.checkout.nexus.transformer.model.le.GatewayEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolver name: {@code GATEWAY_AMOUNT}
 * Returns the gateway event amount ({@link AmountValue}) or null when no gateway event is present.
 */
@Component
public class GatewayAmountResolver implements FieldResolver {

    @Override
    public String name() {
        return "GATEWAY_AMOUNT";
    }

    @Override
    public Object resolve(LeContext ctx, Map<String, Object> params) {
        GatewayEvent gw = ctx.getGateway();
        if (gw == null) {
            return null;
        }
        return gw.getAmount();
    }
}
