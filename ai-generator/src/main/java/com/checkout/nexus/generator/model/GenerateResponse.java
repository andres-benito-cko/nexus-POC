package com.checkout.nexus.generator.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GenerateResponse {
    private boolean success;
    private JsonNode leTransaction;
    private Boolean validationPassed;
    private List<String> errors;
}
