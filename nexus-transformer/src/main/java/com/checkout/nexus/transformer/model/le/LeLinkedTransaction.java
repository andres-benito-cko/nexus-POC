package com.checkout.nexus.transformer.model.le;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
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
    private List<GatewayEvent> gatewayEvents = new ArrayList<>();

    @JsonProperty("balancesChangedEvents")
    private List<BalancesChangedEvent> balancesChangedEvents = new ArrayList<>();

    @JsonProperty("cosEvents")
    private List<CosEvent> cosEvents = new ArrayList<>();

    @JsonProperty("schemeSettlementEvents")
    private List<SchemeSettlementEvent> schemeSettlementEvents = new ArrayList<>();

    @JsonProperty("cashEvents")
    private List<CashEvent> cashEvents = new ArrayList<>();
}
