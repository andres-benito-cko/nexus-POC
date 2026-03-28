package com.checkout.nexus.transformer.engine.pipeline;

import lombok.Value;

@Value
public class StateResult {
    String blockStatus;
    String transactionStatus;
}
