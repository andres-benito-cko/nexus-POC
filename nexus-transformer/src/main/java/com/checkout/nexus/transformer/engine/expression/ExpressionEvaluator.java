package com.checkout.nexus.transformer.engine.expression;

import com.checkout.nexus.transformer.engine.config.FieldMapping;
import com.checkout.nexus.transformer.engine.config.ResolverExpression;
import com.checkout.nexus.transformer.engine.config.WhenCondition;
import com.checkout.nexus.transformer.engine.config.WhenExpression;
import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.resolver.ResolverRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates config-driven expressions against a {@link LeContext}.
 *
 * <p>Handles three expression types:
 * <ol>
 *   <li>{@link String} literal — returned as-is.</li>
 *   <li>{@link FieldMapping} ({@code $field}) — iterates ordered paths via
 *       {@link LeContext#get(String)}, returns first non-null; falls back to
 *       {@code fallback} (or {@link Instant#now()} when the sentinel {@code "$now()"}).</li>
 *   <li>{@link ResolverExpression} ({@code $resolve}) — delegates to {@link ResolverRegistry}.</li>
 *   <li>{@link WhenExpression} ({@code $when}) — evaluates SpEL conditions in order,
 *       returns the first matching {@code then} result or the {@code else} result.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionEvaluator {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final String NOW_SENTINEL = "$now()";

    private final ResolverRegistry resolverRegistry;

    /**
     * Evaluates {@code expression} against {@code ctx} and returns the result.
     *
     * @param expression a String, FieldMapping, ResolverExpression, or WhenExpression
     * @param ctx        the current transaction context
     * @return resolved value or {@code null}
     */
    public Object evaluate(Object expression, LeContext ctx) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof String s) {
            return s;
        }
        if (expression instanceof FieldMapping fm) {
            return evaluateFieldMapping(fm, ctx);
        }
        if (expression instanceof ResolverExpression re) {
            return resolverRegistry.resolve(re.getResolverName(), ctx, re.getParams());
        }
        if (expression instanceof WhenExpression we) {
            return evaluateWhen(we, ctx);
        }
        // Fallback — treat as string
        return expression.toString();
    }

    /**
     * Evaluates a SpEL boolean expression using a context that exposes
     * {@code pillars} (Set&lt;String&gt;) and {@code ctx} (LeContext).
     *
     * @return {@code true} when the condition matches, {@code false} on any error or false result
     */
    public boolean evaluateCondition(String spelExpr, LeContext ctx) {
        if (spelExpr == null || spelExpr.isBlank()) {
            return false;
        }
        try {
            EvaluationContext evalCtx = buildEvaluationContext(ctx);
            Boolean result = SPEL_PARSER.parseExpression(spelExpr).getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("SpEL condition evaluation failed for '{}': {}", spelExpr, e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ private

    private Object evaluateFieldMapping(FieldMapping fm, LeContext ctx) {
        List<String> paths = fm.getFieldPaths();
        if (paths != null) {
            for (String path : paths) {
                Object value = ctx.get(path);
                if (value != null) {
                    return value;
                }
            }
        }
        String fallback = fm.getFallback();
        if (fallback == null) {
            return null;
        }
        if (NOW_SENTINEL.equals(fallback)) {
            return Instant.now().toString();
        }
        return fallback;
    }

    private Object evaluateWhen(WhenExpression we, LeContext ctx) {
        List<WhenCondition> conditions = we.getConditions();
        if (conditions == null) {
            return null;
        }
        for (WhenCondition cond : conditions) {
            if (cond.isElseBranch()) {
                return cond.getElseResult();
            }
            if (evaluateCondition(cond.getIfExpr(), ctx)) {
                return cond.getThenResult();
            }
        }
        return null;
    }

    private EvaluationContext buildEvaluationContext(LeContext ctx) {
        // Use a root object so that `pillars.contains(...)` and `ctx.get(...)` work
        // without the `#` prefix in SpEL expressions.
        SpelContext root = new SpelContext(buildPillarsSet(ctx), ctx);
        return new StandardEvaluationContext(root);
    }

    private Set<String> buildPillarsSet(LeContext ctx) {
        Set<String> pillars = new HashSet<>();
        for (String name : List.of("GATEWAY", "FIAPI", "COS", "SD", "CASH")) {
            if (ctx.hasPillar(name)) {
                pillars.add(name);
            }
        }
        return Collections.unmodifiableSet(pillars);
    }
}
