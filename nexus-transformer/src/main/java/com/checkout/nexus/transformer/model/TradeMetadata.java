package com.checkout.nexus.transformer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeMetadata {

    @JsonProperty("scheme_code")
    private String schemeCode;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("acquirer_name")
    private String acquirerName;

    @JsonProperty("acquirer_country")
    private String acquirerCountry;

    @JsonProperty("cash_batch_id")
    private String cashBatchId;

    @JsonProperty("client_settlement_type")
    private String clientSettlementType;

    @JsonProperty("merchant_category_code")
    private String merchantCategoryCode;

    @JsonProperty("source_event_type")
    private String sourceEventType;

    @JsonProperty("invoice_number")
    private String invoiceNumber;

    @JsonProperty("settlement_service_name")
    private String settlementServiceName;

    @JsonProperty("settlement_country_code")
    private String settlementCountryCode;

    @JsonProperty("central_processing_date")
    private String centralProcessingDate;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("enhanced_action_type")
    private String enhancedActionType;

    @JsonProperty("reconciliation_reference")
    private String reconciliationReference;

    @JsonProperty("scheme_partner_identifier")
    private String schemePartnerIdentifier;

    @JsonProperty("payout_id")
    private String payoutId;

    @JsonProperty("billing_descriptor")
    private String billingDescriptor;

    @JsonProperty("is_net_settled")
    private Boolean isNetSettled;
}
