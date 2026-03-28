package com.checkout.nexus.transformer.engine.expression;

import com.checkout.nexus.transformer.engine.context.LeContext;

import java.util.Set;

/**
 * Root object for SpEL expressions — exposes {@code pillars} (Set&lt;String&gt;) and
 * {@code ctx} ({@link LeContext}) as top-level properties so that expressions can
 * use {@code pillars.contains('SD')} and {@code ctx.get('SD.metadata.scheme')} without
 * the {@code #} prefix.
 */
public class SpelContext {

    private final Set<String> pillars;
    private final LeContext ctx;

    public SpelContext(Set<String> pillars, LeContext ctx) {
        this.pillars = pillars;
        this.ctx = ctx;
    }

    public Set<String> getPillars() {
        return pillars;
    }

    public LeContext getCtx() {
        return ctx;
    }
}
