package com.checkout.nexus.generator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProgressEvent {
    private String step;
    private String message;
}
