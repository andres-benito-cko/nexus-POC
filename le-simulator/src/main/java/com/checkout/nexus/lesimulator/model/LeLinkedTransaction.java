package com.checkout.nexus.lesimulator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeLinkedTransaction {

    @JsonProperty("id")
    private String id;

    @JsonProperty("actionRootId")
    private String actionRootId;

    @JsonProperty("actionId")
    private String actionId;

    @JsonProperty("transactionVersion")
    private int transactionVersion;

    @JsonProperty("gatewayEvents")
    @Builder.Default
    private List<GatewayEvent> gatewayEvents = new ArrayList<>();

    @JsonProperty("balancesChangedEvents")
    @Builder.Default
    private List<BalancesChangedEvent> balancesChangedEvents = new ArrayList<>();

    @JsonProperty("cosEvents")
    @Builder.Default
    private List<CosEvent> cosEvents = new ArrayList<>();

    @JsonProperty("schemeSettlementEvents")
    @Builder.Default
    private List<SchemeSettlementEvent> schemeSettlementEvents = new ArrayList<>();

    @JsonProperty("cashEvents")
    @Builder.Default
    private List<CashEvent> cashEvents = new ArrayList<>();
}
