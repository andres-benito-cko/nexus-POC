package com.checkout.nexus.transformer.engine.resolver;

import com.checkout.nexus.transformer.engine.config.ConfigHolder;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.model.Fee;
import com.checkout.nexus.transformer.model.le.CosEvent;
import com.checkout.nexus.transformer.model.le.SchemeSettlementEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolver name: {@code SCHEME_FEES}
 *
 * <p>Params:
 * <ul>
 *   <li>{@code source_priority} — ordered list of sources to try (e.g. {@code [SD, COS]})</li>
 * </ul>
 *
 * <p>Returns a {@link List}&lt;{@link Fee}&gt;.
 */
@Component
@RequiredArgsConstructor
public class SchemeFeesResolver implements FieldResolver {

    private final ConfigHolder config;

    @Override
    public String name() {
        return "SCHEME_FEES";
    }

    @Override
    public Object resolve(LeContext ctx, Map<String, Object> params) {
        List<String> priority = sourcePriority(params);
        Map<String, String> sdMapping = feeTypeMapping("sd");
        Map<String, String> cosMapping = feeTypeMapping("cos");

        for (String source : priority) {
            if ("SD".equals(source) && ctx.hasPillar("SD")) {
                List<Fee> fees = buildSdFees(ctx, sdMapping);
                if (!fees.isEmpty()) {
                    return fees;
                }
            }
            if ("COS".equals(source) && ctx.hasPillar("COS")) {
                List<Fee> fees = buildCosFees(ctx, cosMapping);
                if (!fees.isEmpty()) {
                    return fees;
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Fee> buildSdFees(LeContext ctx, Map<String, String> mapping) {
        SchemeSettlementEvent sd = ctx.getSd();
        if (sd == null || sd.getPayload() == null || sd.getPayload().getFees() == null) {
            return Collections.emptyList();
        }
        List<Fee> fees = new ArrayList<>();
        int counter = 1;
        for (SchemeSettlementEvent.SdFee sdFee : sd.getPayload().getFees()) {
            String feeType = mapping.getOrDefault(sdFee.getType(), "SCHEME_FEE");
            Double taxAmount = sdFee.getTaxAmount() > 0 ? sdFee.getTaxAmount() : null;
            fees.add(Fee.builder()
                    .feeId("_F" + counter++)
                    .feeType(feeType)
                    .feeAmount(sdFee.getRoundedAmount())
                    .feeCurrency(sdFee.getCurrencyCode())
                    .feeStatus("ACTUAL")
                    .taxAmount(taxAmount)
                    .build());
        }
        return fees;
    }

    private List<Fee> buildCosFees(LeContext ctx, Map<String, String> mapping) {
        List<CosEvent> cosEvents = ctx.getAllCos();
        if (cosEvents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Fee> fees = new ArrayList<>();
        int counter = 1;
        for (CosEvent cos : cosEvents) {
            if (cos.getPayload() == null || cos.getPayload().getFee() == null) {
                continue;
            }
            String rawFeeType = cos.getPayload().getFeeType();
            String feeType = mapping.getOrDefault(rawFeeType, "SCHEME_FEE");
            Double taxAmount = null;
            String taxCurrency = null;
            if (cos.getPayload().getVat() != null && cos.getPayload().getVat().getValue() > 0) {
                taxAmount = cos.getPayload().getVat().getValue();
                taxCurrency = cos.getPayload().getVat().getCurrencyCode();
            }
            Boolean passthrough = "Passthrough".equals(cos.getPayload().getFeeSubType()) ? Boolean.TRUE : null;
            fees.add(Fee.builder()
                    .feeId("_F" + counter++)
                    .feeType(feeType)
                    .feeAmount(cos.getPayload().getFee().getValue())
                    .feeCurrency(cos.getPayload().getFee().getCurrencyCode())
                    .feeStatus("PREDICTED")
                    .taxAmount(taxAmount)
                    .taxCurrency(taxCurrency)
                    .passthrough(passthrough)
                    .build());
        }
        return fees;
    }

    private Map<String, String> feeTypeMapping(String source) {
        if (config.getFeeTypeMappings() == null) {
            return Collections.emptyMap();
        }
        return config.getFeeTypeMappings().getOrDefault(source, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private List<String> sourcePriority(Map<String, Object> params) {
        if (params != null && params.containsKey("source_priority")) {
            Object val = params.get("source_priority");
            if (val instanceof List<?>) {
                return (List<String>) val;
            }
        }
        return List.of("SD", "COS");
    }
}
