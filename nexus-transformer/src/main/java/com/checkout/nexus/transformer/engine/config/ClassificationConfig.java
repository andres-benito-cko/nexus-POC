package com.checkout.nexus.transformer.engine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationConfig {

    @JsonProperty("family")
    private List<FamilyRule> family;

    @JsonProperty("type")
    private TypeClassificationConfig type;
}
