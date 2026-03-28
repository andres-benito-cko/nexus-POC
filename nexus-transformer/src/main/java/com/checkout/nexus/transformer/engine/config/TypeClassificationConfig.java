package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeClassificationConfig {

    /** Ordered list of pillar names to check when determining trade type. */
    @JsonProperty("priority")
    private List<String> priority;

    /** Maps a pillar name to the dot-path field that carries the raw type value. */
    @JsonProperty("field_per_source")
    private Map<String, String> fieldPerSource;

    /** Maps canonical trade-type names to the raw values recognised from pillar events. */
    @JsonProperty("mapping")
    private Map<String, List<String>> mapping;
}
