package com.checkout.nexus.transformer.engine.pipeline;

import com.checkout.nexus.transformer.engine.config.FamilyRule;
import com.checkout.nexus.transformer.engine.config.NexusEngineConfig;
import com.checkout.nexus.transformer.engine.config.TypeClassificationConfig;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.expression.ExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Classifies a transaction into a product type (ACQUIRING / PAYOUT / TOPUP / CASH)
 * and a transaction type (CAPTURE / REFUND / etc.) using rules from {@link NexusEngineConfig}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Classifier {

    private final NexusEngineConfig config;
    private final ExpressionEvaluator evaluator;

    public ClassificationResult classify(LeContext ctx) {
        String family = classifyFamily(ctx);
        String type = classifyType(ctx);
        return new ClassificationResult(family, type);
    }

    // ------------------------------------------------------------------ family

    private String classifyFamily(LeContext ctx) {
        List<FamilyRule> rules = config.getClassification().getFamily();
        for (FamilyRule rule : rules) {
            if (rule.isDefaultRule()) {
                return rule.effectiveResult();
            }
            if (evaluator.evaluateCondition(rule.getWhen(), ctx)) {
                return rule.effectiveResult();
            }
        }
        return "ACQUIRING"; // ultimate fallback
    }

    // ------------------------------------------------------------------ type

    private String classifyType(LeContext ctx) {
        TypeClassificationConfig typeCfg = config.getClassification().getType();
        List<String> priority = typeCfg.getPriority();
        Map<String, String> fieldPerSource = typeCfg.getFieldPerSource();
        Map<String, List<String>> mapping = typeCfg.getMapping();

        for (String source : priority) {
            if (!ctx.hasPillar(source)) {
                continue;
            }
            String fieldPath = fieldPerSource.get(source);
            if (fieldPath == null) {
                continue;
            }
            Object rawValue = ctx.get(source + "." + fieldPath);
            if (rawValue == null) {
                continue;
            }
            String rawStr = rawValue.toString();
            // First pass: exact match
            for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
                for (String candidate : entry.getValue()) {
                    if (candidate.equals(rawStr)) {
                        return entry.getKey();
                    }
                }
            }
            // Second pass: rawStr starts-with or contains candidate
            // Use longest match to avoid CAPTURE matching inside CARD_PAYOUT_CAPTURE
            String bestMatch = null;
            int bestLen = 0;
            for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
                for (String candidate : entry.getValue()) {
                    if (rawStr.contains(candidate) && candidate.length() > bestLen) {
                        bestMatch = entry.getKey();
                        bestLen = candidate.length();
                    }
                }
            }
            if (bestMatch != null) {
                return bestMatch;
            }
        }

        return "CAPTURE"; // default fallback
    }
}
