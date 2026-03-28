package com.checkout.nexus.transformer.service;

import com.checkout.nexus.transformer.model.NexusTransaction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory ring buffer storing the last 100 transformed Nexus transactions.
 */
@Component
public class TransactionStore {

    private static final int MAX_SIZE = 100;

    private final ConcurrentLinkedDeque<NexusTransaction> buffer = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, NexusTransaction> index = new ConcurrentHashMap<>();

    public void add(NexusTransaction txn) {
        // Upsert by transaction_id (later versions replace earlier)
        NexusTransaction existing = index.put(txn.getTransactionId(), txn);
        if (existing != null) {
            buffer.remove(existing);
        }
        buffer.addFirst(txn);

        // Trim to max size
        while (buffer.size() > MAX_SIZE) {
            NexusTransaction removed = buffer.removeLast();
            if (removed != null) {
                index.remove(removed.getTransactionId(), removed);
            }
        }
    }

    public Optional<NexusTransaction> get(String transactionId) {
        return Optional.ofNullable(index.get(transactionId));
    }

    public List<NexusTransaction> getLatest(int limit) {
        List<NexusTransaction> result = new ArrayList<>();
        Iterator<NexusTransaction> it = buffer.iterator();
        int count = 0;
        while (it.hasNext() && count < limit) {
            result.add(it.next());
            count++;
        }
        return result;
    }
}
