package com.checkout.nexus.transformer.engine;

import com.checkout.nexus.transformer.engine.context.LeContext;
import com.checkout.nexus.transformer.engine.pipeline.ClassificationResult;
import com.checkout.nexus.transformer.engine.pipeline.Classifier;
import com.checkout.nexus.transformer.engine.pipeline.StateResult;
import com.checkout.nexus.transformer.engine.pipeline.StateMachineRunner;
import com.checkout.nexus.transformer.engine.pipeline.BlockAssembler;
import com.checkout.nexus.transformer.model.NexusBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Entry point for the Nexus Configurable Engine.
 *
 * <p>Orchestrates the three pipeline stages:
 * <ol>
 *   <li>{@link Classifier} — determines product type and transaction type</li>
 *   <li>{@link StateMachineRunner} — determines block and transaction statuses</li>
 *   <li>{@link BlockAssembler} — assembles the full {@link NexusBlock}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusEngine {

    private final Classifier classifier;
    private final StateMachineRunner stateMachineRunner;
    private final BlockAssembler blockAssembler;

    /**
     * Transforms a wrapped LE transaction into a {@link NexusBlock}.
     *
     * @param ctx the wrapped LE context
     * @return the assembled Nexus transaction
     */
    public NexusBlock transform(LeContext ctx) {
        ClassificationResult classification = classifier.classify(ctx);
        log.debug("Classified: family={}, type={}", classification.getProductType(), classification.getTransactionType());

        StateResult state = stateMachineRunner.run(ctx, classification.getProductType());
        log.debug("State: blockStatus={}, transactionStatus={}", state.getBlockStatus(), state.getTransactionStatus());

        NexusBlock result = blockAssembler.assemble(ctx, classification, state);
        log.debug("Assembled transaction: id={}", result.getNexusId());

        return result;
    }
}
