package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.le.AmountValue;
import com.checkout.nexus.transformer.model.le.CashEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolver name: {@code CASH_AMOUNT}
 * Returns the cash event's standard payload amount ({@link AmountValue}) or null.
 */
@Component
public class CashAmountResolver implements FieldResolver {

    @Override
    public String name() {
        return "CASH_AMOUNT";
    }

    @Override
    public Object resolve(LeContext ctx, Map<String, Object> params) {
        CashEvent cash = ctx.getCash();
        if (cash == null || cash.getStandardPayload() == null) {
            return null;
        }
        return cash.getStandardPayload().getAmount();
    }
}
