package com.checkout.nexus.transformer.engine.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for {@link NexusEngineConfig} that supports atomic hot-swaps.
 * Pipeline components inject this instead of the config directly so that
 * activating a new config in nexus-api takes effect without a restart.
 */
public class ConfigHolder {

    private final AtomicReference<NexusEngineConfig> ref;

    public ConfigHolder(NexusEngineConfig initial) {
        this.ref = new AtomicReference<>(initial);
    }

    public void update(NexusEngineConfig newConfig) {
        ref.set(newConfig);
    }

    public String getVersion() {
        return ref.get().getVersion();
    }

    public ClassificationConfig getClassification() {
        return ref.get().getClassification();
    }

    public Map<String, StateMachineConfig> getStateMachines() {
        return ref.get().getStateMachines();
    }

    public Map<String, Map<String, TransactionConfig>> getTransactions() {
        return ref.get().getTransactions();
    }

    public Map<String, FieldMapping> getFieldMappings() {
        return ref.get().getFieldMappings();
    }

    public Map<String, Map<String, String>> getFeeTypeMappings() {
        return ref.get().getFeeTypeMappings();
    }
}
