package com.checkout.nexus.transformer.engine;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.pipeline.ClassificationResult;
import com.checkout.nexus.transformer.engine.pipeline.Classifier;
import com.checkout.nexus.transformer.engine.pipeline.StateResult;
import com.checkout.nexus.transformer.engine.pipeline.StateMachineRunner;
import com.checkout.nexus.transformer.engine.pipeline.TransactionAssembler;
import com.checkout.nexus.transformer.model.NexusTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Entry point for the Nexus Configurable Engine.
 *
 * <p>Orchestrates the three pipeline stages:
 * <ol>
 *   <li>{@link Classifier} — determines trade family and type</li>
 *   <li>{@link StateMachineRunner} — determines transaction and trade statuses</li>
 *   <li>{@link TransactionAssembler} — assembles the full {@link NexusTransaction}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusEngine {

    private final Classifier classifier;
    private final StateMachineRunner stateMachineRunner;
    private final TransactionAssembler transactionAssembler;

    /**
     * Transforms a wrapped LE transaction into a {@link NexusTransaction}.
     *
     * @param ctx the wrapped LE context
     * @return the assembled Nexus transaction
     */
    public NexusTransaction transform(LeContext ctx) {
        ClassificationResult classification = classifier.classify(ctx);
        log.debug("Classified: family={}, type={}", classification.getTradeFamily(), classification.getTradeType());

        StateResult state = stateMachineRunner.run(ctx, classification.getTradeFamily());
        log.debug("State: transactionStatus={}, tradeStatus={}", state.getTransactionStatus(), state.getTradeStatus());

        NexusTransaction result = transactionAssembler.assemble(ctx, classification, state);
        log.debug("Assembled transaction: id={}", result.getTransactionId());

        return result;
    }
}
