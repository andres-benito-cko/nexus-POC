package com.checkout.nexus.transformer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NexusBlock {

    @JsonProperty("nexus_id")
    private String nexusId;

    @JsonProperty("parent_nexus_id")
    private String parentNexusId;

    @JsonProperty("action_id")
    private String actionId;

    @JsonProperty("action_root_id")
    private String actionRootId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("entity")
    private Entity entity;

    @JsonProperty("cko_entity_id")
    private String ckoEntityId;

    @JsonProperty("processed_at")
    private String processedAt;

    @JsonProperty("period_start")
    private String periodStart;

    @JsonProperty("period_end")
    private String periodEnd;

    @JsonProperty("transactions")
    private List<Transaction> trades;
}
