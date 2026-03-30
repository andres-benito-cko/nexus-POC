package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.config.LeBatchConfig;
import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import com.checkout.nexus.lesimulator.model.SchemeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class RandomSequenceEmitter {

    private final KafkaTemplate<String, LeLinkedTransaction> kafkaTemplate;
    private final LeBatchConfig config;
    private final ScenarioLoader scenarioLoader;

    private static final String[] CLIENT_IDS = {
        "cli_acme_corp", "cli_global_ltd", "cli_retail_eu", "cli_fintech_uk"
    };

    public void emitSequence(int delayMs) throws InterruptedException {
        SchemeProfile scheme  = pickScheme();
        double amount         = Math.round((10 + ThreadLocalRandom.current().nextDouble() * 990) * 100.0) / 100.0;
        String clientId       = CLIENT_IDS[ThreadLocalRandom.current().nextInt(CLIENT_IDS.length)];

        List<LeLinkedTransaction> versions = scenarioLoader.buildRandomCaptureSequence(scheme, amount, clientId);

        for (int i = 0; i < versions.size(); i++) {
            LeLinkedTransaction version = versions.get(i);
            // amount/currency are uniform across all 4 versions in random capture sequences
            log.info("Producing random LE transaction: actionId={}, version={}, scheme={}, amount={} {}",
                version.getActionId(), version.getTransactionVersion(),
                scheme.schemeName(), amount, scheme.defaultCurrency());
            kafkaTemplate.send(config.getTopicName(), version.getActionId(), version);
            if (delayMs > 0 && i < versions.size() - 1) {
                Thread.sleep(delayMs);
            }
        }
    }

    private SchemeProfile pickScheme() {
        double r = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        List<SchemeProfile> all = SchemeProfile.ALL;
        for (int i = 0; i < all.size(); i++) {
            cumulative += SchemeProfile.VOLUME_WEIGHTS[i];
            if (r < cumulative) {
                return all.get(i);
            }
        }
        return SchemeProfile.VISA;
    }
}
