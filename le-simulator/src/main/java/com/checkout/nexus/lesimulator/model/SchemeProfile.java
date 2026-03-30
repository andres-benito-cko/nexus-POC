package com.checkout.nexus.lesimulator.model;

import java.util.List;

/**
 * Per-scheme field values used by ScenarioLoader and RandomSequenceEmitter.
 *
 * Fields marked "TODO: BQ Qn" must be updated from production-distributions.md
 * results before this plan is considered complete (see Task 7).
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
        "VISA",        // TODO: BQ Q10 confirm casing
        "visa",        // TODO: BQ Q10 confirm casing
        "EUR",
        0.0048,        // TODO: BQ Q4 avg_ic_fee_pct / 100
        0.0009,        // TODO: BQ Q4 avg_scheme_fee_pct / 100
        "ISS",         // TODO: BQ Q9 dominant service for Visa
        "GB"
    );

    public static final SchemeProfile MASTERCARD = new SchemeProfile(
        "Mastercard",
        "MASTERCARD",  // TODO: BQ Q10
        "mastercard",  // TODO: BQ Q10
        "EUR",
        0.0050,        // TODO: BQ Q4
        0.0010,        // TODO: BQ Q4
        "EANSS",       // TODO: BQ Q9
        "GB"
    );

    public static final SchemeProfile AMEX = new SchemeProfile(
        "Amex",
        "AMEX",        // TODO: BQ Q10 (may not appear in Balances if no COS)
        "amex",        // TODO: BQ Q10
        "USD",         // TODO: BQ Q7 confirm dominant currency
        0.0175,        // TODO: BQ Q4
        0.0000,        // TODO: BQ Q4 (Amex typically bundles fees)
        "EANSS",       // TODO: BQ Q9
        "GB"
    );

    // Carte Bancaire is simulated as complete even though production data has gaps.
    // All field values require BQ validation — extrapolated from Visa/MC structure.
    public static final SchemeProfile CARTE_BANCAIRE = new SchemeProfile(
        "Carte Bancaire", // TODO: BQ Q6 — confirm exact scheme name string in production
        "CB",             // TODO: BQ Q10 Q6
        "cb",             // TODO: BQ Q10 Q6
        "EUR",
        0.0020,           // TODO: BQ Q4 (if data exists) else use regulatory cap ~0.2%
        0.0005,           // TODO: BQ Q4
        "EU00082602",     // TODO: BQ Q9
        "FR"
    );

    public static final SchemeProfile JCB = new SchemeProfile(
        "JCB",
        "JCB",         // TODO: BQ Q10 Q6
        "jcb",         // TODO: BQ Q10 Q6 — contract doc shows "jcb" in COS
        "JPY",         // TODO: BQ Q7 confirm dominant currency
        0.0140,        // TODO: BQ Q4
        0.0000,        // TODO: BQ Q4
        "AP00070201",  // TODO: BQ Q9
        "JP"
    );

    public static final SchemeProfile DISCOVER = new SchemeProfile(
        "Discover",
        "DISCOVER",    // TODO: BQ Q10 Q6
        "discover",    // TODO: BQ Q10 Q6 — contract doc shows "discover" in COS
        "USD",         // TODO: BQ Q7
        0.0155,        // TODO: BQ Q4
        0.0000,        // TODO: BQ Q4
        "US00000001",  // TODO: BQ Q9
        "US"
    );

    /** All profiles in volume order (highest to lowest). */
    public static final List<SchemeProfile> ALL = List.of(
        VISA, MASTERCARD, AMEX, CARTE_BANCAIRE, JCB, DISCOVER
    );

    /**
     * Volume weights for random sampling, indexed parallel to ALL.
     * TODO: BQ Q1a — replace with actual production volume distribution.
     * Current values are rough estimates only.
     */
    public static final double[] VOLUME_WEIGHTS = {0.43, 0.40, 0.10, 0.05, 0.01, 0.01};
}
