package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.Fee;
import com.checkout.nexus.transformer.model.le.BalancesChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolver name: {@code FUNDING_FEES}
 *
 * <p>Iterates FIAPI actions where {@code amountType == "fee"}, maps the {@code feeType}
 * using the {@code fiapi} fee type mapping from config, and returns a
 * {@link List}&lt;{@link Fee}&gt; with status ACTUAL.
 */
@Component
@RequiredArgsConstructor
public class FundingFeesResolver implements FieldResolver {

    private final NexusEngineConfig config;

    @Override
    public String name() {
        return "FUNDING_FEES";
    }

    @Override
    public Object resolve(LeContext ctx, Map<String, Object> params) {
        BalancesChangedEvent fiapi = ctx.getFiapi();
        if (fiapi == null || fiapi.getActions() == null) {
            return Collections.emptyList();
        }

        Map<String, String> fiapiMapping = fiapiTypeMapping();
        List<Fee> fees = new ArrayList<>();
        int counter = 1;

        for (BalancesChangedEvent.Action action : fiapi.getActions()) {
            if (action.getActionMetadata() == null) {
                continue;
            }
            if (!"fee".equals(action.getActionMetadata().getAmountType())) {
                continue;
            }
            String rawFeeType = action.getActionMetadata().getFeeType();
            String feeType = fiapiMapping.getOrDefault(rawFeeType, "PROCESSING_FEE");

            fees.add(Fee.builder()
                    .feeId("_F" + counter++)
                    .feeType(feeType)
                    .feeAmount(0) // Amount from changes (simplified; actual amount needs holding amount)
                    .feeCurrency(extractCurrency(action))
                    .feeStatus("ACTUAL")
                    .build());
        }
        return fees;
    }

    private String extractCurrency(BalancesChangedEvent.Action action) {
        if (action.getChanges() == null) return "EUR";
        if (action.getChanges().getPending() == null) return "EUR";
        if (action.getChanges().getPending().getHoldingAmount() == null) return "EUR";
        String currency = action.getChanges().getPending().getHoldingAmount().getCurrencyCode();
        return currency != null ? currency : "EUR";
    }

    private Map<String, String> fiapiTypeMapping() {
        if (config.getFeeTypeMappings() == null) {
            return Collections.emptyMap();
        }
        return config.getFeeTypeMappings().getOrDefault("fiapi", Collections.emptyMap());
    }
}
