package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a {@code $field} mapping in the YAML config:
 *
 * <pre>
 * entity_id:
 *   $field: ["FIAPI.metadata.clientId", "SD.metadata.clientId"]
 *   $fallback: "unknown_entity"
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldMapping {

    /** Ordered list of dot-paths to try; first non-null value is returned. */
    @JsonProperty("$field")
    private List<String> fieldPaths;

    /**
     * Fallback value when all field paths resolve to null.
     * May be the special sentinel {@code "$now()"} meaning "current timestamp".
     */
    @JsonProperty("$fallback")
    private String fallback;
}
