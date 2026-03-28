package com.checkout.nexus.transformer.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.checkout.nexus.transformer.model.NexusBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates a {@link NexusBlock} against the Nexus JSON Schema.
 *
 * <p>The schema is loaded once from {@code nexus.schema.json} on the classpath.
 * Validation uses the {@code com.networknt:json-schema-validator} library with
 * JSON Schema Draft-7.
 */
@Slf4j
@Component
public class NexusValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonSchema schema;

    public NexusValidator() {
        this.schema = loadSchema();
    }

    /**
     * Validates a {@link NexusBlock}.
     *
     * @param tx the transaction to validate
     * @return a {@link ValidationResult} with isValid flag and error messages
     */
    public ValidationResult validate(NexusBlock tx) {
        try {
            JsonNode node = MAPPER.valueToTree(tx);
            Set<ValidationMessage> messages = schema.validate(node);
            if (messages.isEmpty()) {
                return new ValidationResult(true, List.of());
            }
            List<String> errors = new ArrayList<>();
            for (ValidationMessage msg : messages) {
                errors.add(msg.getMessage());
            }
            log.debug("Validation failed for nexusId={} with {} errors",
                    tx.getNexusId(), errors.size());
            return new ValidationResult(false, errors);
        } catch (Exception e) {
            log.error("Unexpected error during schema validation", e);
            return new ValidationResult(false, List.of("Validation error: " + e.getMessage()));
        }
    }

    private JsonSchema loadSchema() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("nexus.schema.json")) {
            if (in == null) {
                throw new IllegalStateException("nexus.schema.json not found on classpath");
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            return factory.getSchema(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Nexus JSON schema", e);
        }
    }

    // ------------------------------------------------------------------ result type

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = List.copyOf(errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
