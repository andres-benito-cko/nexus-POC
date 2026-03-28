package com.checkout.nexus.transformer.engine.pipeline;

import lombok.Value;

@Value
public class StateResult {
    String transactionStatus;
    String tradeStatus;
}
