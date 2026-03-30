package com.checkout.nexus.lesimulator.service;

import com.checkout.nexus.lesimulator.config.LeBatchConfig;
import com.checkout.nexus.lesimulator.model.LeLinkedTransaction;
import com.checkout.nexus.lesimulator.model.SchemeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final KafkaTemplate<String, LeLinkedTransaction> kafkaTemplate;
    private final LeBatchConfig config;
    private final ScenarioLoader scenarioLoader;
    private final RandomSequenceEmitter randomSequenceEmitter;

    private TaskScheduler taskScheduler;
    private ScheduledFuture<?> randomFuture;
    private final AtomicReference<String> mode             = new AtomicReference<>("idle");
    private final AtomicReference<String> currentScenario  = new AtomicReference<>(null);
    private final AtomicInteger messagesProduced            = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("simulator-");
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    public void playScenario(String scenarioId, int delayMs, SchemeProfile scheme) {
        if (!"idle".equals(mode.get())) {
            throw new IllegalStateException("Simulator is already running in mode: " + mode.get());
        }
        mode.set("scenario");
        currentScenario.set(scenarioId);

        taskScheduler.schedule(() -> {
            try {
                List<LeLinkedTransaction> versions = scenarioLoader.loadScenario(scenarioId, scheme);
                for (LeLinkedTransaction version : versions) {
                    log.info("Producing LE transaction: actionId={}, version={}, scheme={}",
                        version.getActionId(), version.getTransactionVersion(), scheme.schemeName());
                    kafkaTemplate.send(config.getTopicName(), version.getActionId(), version);
                    messagesProduced.incrementAndGet();
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error playing scenario {}", scenarioId, e);
            } finally {
                mode.set("idle");
                currentScenario.set(null);
            }
        }, java.time.Instant.now());
    }

    public void startRandom(int intervalMs) {
        if (!"idle".equals(mode.get())) {
            throw new IllegalStateException("Simulator is already running in mode: " + mode.get());
        }
        mode.set("random");

        randomFuture = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                randomSequenceEmitter.emitSequence(500);
                messagesProduced.addAndGet(4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in random sequence generation", e);
            }
        }, Duration.ofMillis(intervalMs));
    }

    public void stopRandom() {
        if (randomFuture != null) {
            randomFuture.cancel(false);
            randomFuture = null;
        }
        mode.set("idle");
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "mode", mode.get(),
            "currentScenario", currentScenario.get() != null ? currentScenario.get() : "",
            "messagesProduced", messagesProduced.get()
        );
    }
}
