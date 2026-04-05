package com.checkout.nexus.lesimulator.model;

import java.util.List;

/**
 * Per-scheme field values used by ScenarioLoader and RandomSequenceEmitter.
 *
 * All values confirmed from BQ production-distributions.md (2026-03-17 to 2026-03-20)
 * unless noted as simulation estimates.
 */
public record SchemeProfile(
    String schemeName,
    String paymentMethodBalances,
    String paymentMethodCos,
    String defaultCurrency,
    double interchangeFeeRate,
    double schemeFeeRate,
    String settlementServiceName,
    String acquirerCountry
) {

    public static final SchemeProfile VISA = new SchemeProfile(
        "Visa",
        "VISA",        // confirmed Q10: 39M+ records
        "visa",        // confirmed Q10: 39M+ records
        "USD",         // dominant per Q4: 8.7M USD vs 7.2M GBP vs 5.7M EUR
        0.0055,        // Simulation rate for POC; production COS reports IC as NULL
        0.0046,        // Q4 avg_scheme_fee_pct for Visa/EUR: 0.4563% / 100
        "ISS",         // confirmed Q9: 49.01% dominant for Visa
        "GB"
    );

    public static final SchemeProfile MASTERCARD = new SchemeProfile(
        "Mastercard",
        "MASTERCARD",  // confirmed Q10: 29M+ records
        "mastercard",  // confirmed Q10: 29M+ records
        "EUR",         // confirmed Q7: 25.79% dominant for Mastercard
        0.0045,        // Simulation rate for POC; production COS reports IC as NULL
        0.0031,        // Q4 avg_scheme_fee_pct for Mastercard/EUR: 0.3094% / 100
        "EU00000008",  // confirmed Q9: 25.43% dominant for Mastercard
        "GB"
    );

    public static final SchemeProfile AMEX = new SchemeProfile(
        "AMEX",        // confirmed Q1a: scheme appears as "AMEX" in SD events
        "AMEX",        // confirmed Q1c: 5.2M records
        "amex",        // confirmed Q1b: 299,859 COS records
        "USD",         // confirmed Q7: 72.63% dominant for AMEX
        0.0150,        // Simulation rate for POC; production COS reports IC as NULL
        0.0000,        // scheme fee not reliably derived from Q4 for AMEX sample
        "4641456538",  // confirmed Q9: 63.57% dominant for AMEX; AMEX settlement service IDs are numeric strings
        "GB"
    );

    // Carte Bancaire has no SD events in production (Q6). All values are simulation estimates
    // except paymentMethodBalances which is confirmed from Q1c.
    public static final SchemeProfile CARTE_BANCAIRE = new SchemeProfile(
        "Carte Bancaire", // simulation value — CB has no SD events; name cannot be confirmed from production SD
        "CARTE BANCAIRE", // confirmed Q1c: standalone form, 1,414,800 records
        "cb",             // simulation value — CB has negligible COS presence
        "EUR",
        0.0030,           // Simulation rate for POC; production COS reports IC as NULL
        0.0005,           // simulation estimate — CB has no production COS data
        "EU00082602",     // simulation value — CB has no SD events in production
        "FR"
    );

    // JCB settlements appear as "JCN" (Japan Credit Network) in production scheme_settlement_metadata.scheme
    public static final SchemeProfile JCB = new SchemeProfile(
        "JCN",             // confirmed Q1a: SD scheme name is "JCN", not "JCB"
        "JcbDomestic",     // confirmed Q1c: dominant form with 612,254 records
        "jcb",             // confirmed Q1b: 274,998 COS records
        "JPY",
        0.0060,            // Simulation rate for POC; production COS reports IC as NULL
        0.0000,
        "JCN006308223",    // confirmed Q9: 100% for JCN
        "JP"
    );

    // Discover has no SD events in production (Q6). "Discover" does not appear as a scheme in SD;
    // Diners Club appears as "DCI". Values are simulation estimates except paymentMethodBalances.
    public static final SchemeProfile DISCOVER = new SchemeProfile(
        "Discover",        // simulation value — Discover has no SD events in production (appears as DCI/Diners Club)
        "Discover",        // confirmed Q1c: dominant form with 6,207,931 records
        "discover",        // only 238 COS records — essentially simulation value
        "USD",
        0.0100,            // Simulation rate for POC; production COS reports IC as NULL
        0.0000,
        "US00000001",      // simulation value — Discover has no SD events in production (appears as DCI/Diners Club)
        "US"
    );

    /** All profiles in volume order (highest to lowest). */
    public static final List<SchemeProfile> ALL = List.of(
        VISA, MASTERCARD, AMEX, CARTE_BANCAIRE, JCB, DISCOVER
    );

    /**
     * Volume weights for random sampling, indexed parallel to ALL.
     * Derived from Q1a unique_action_ids (2026-03-17 to 2026-03-20):
     * Visa 30.4M (57.5%), Mastercard 21.7M (41.0%), JCN 568K (1.07%), AMEX 245K (0.46%).
     * CB and Discover have no SD events in production; given nominal weight for simulator coverage.
     */
    public static final double[] VOLUME_WEIGHTS = {0.55, 0.38, 0.03, 0.01, 0.02, 0.01};
}
