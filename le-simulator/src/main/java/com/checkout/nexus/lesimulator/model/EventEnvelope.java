package com.checkout.nexus.lesimulator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventEnvelope {

    @JsonProperty("actionId")
    private String actionId;

    @JsonProperty("actionRootId")
    private String actionRootId;

    @JsonProperty("originUniqueEventId")
    private String originUniqueEventId;

    @JsonProperty("originEventTimestamp")
    private String originEventTimestamp;

    @JsonProperty("correctorVersion")
    private int correctorVersion;

    @JsonProperty("processedCount")
    private int processedCount;
}
