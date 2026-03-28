package com.checkout.nexus.transformer.engine.pipeline;

import lombok.Value;

@Value
public class ClassificationResult {
    String productType;
    String transactionType;
}
