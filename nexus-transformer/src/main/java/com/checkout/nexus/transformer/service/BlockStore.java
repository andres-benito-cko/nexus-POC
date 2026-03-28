package com.checkout.nexus.transformer.service;

import com.checkout.nexus.transformer.model.NexusBlock;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory ring buffer storing the last 100 transformed Nexus transactions.
 */
@Component
public class BlockStore {

    private static final int MAX_SIZE = 100;

    private final ConcurrentLinkedDeque<NexusBlock> buffer = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, NexusBlock> index = new ConcurrentHashMap<>();

    public void add(NexusBlock txn) {
        // Upsert by nexus_id (later versions replace earlier)
        NexusBlock existing = index.put(txn.getNexusId(), txn);
        if (existing != null) {
            buffer.remove(existing);
        }
        buffer.addFirst(txn);

        // Trim to max size
        while (buffer.size() > MAX_SIZE) {
            NexusBlock removed = buffer.removeLast();
            if (removed != null) {
                index.remove(removed.getNexusId(), removed);
            }
        }
    }

    public Optional<NexusBlock> get(String nexusId) {
        return Optional.ofNullable(index.get(nexusId));
    }

    public List<NexusBlock> getLatest(int limit) {
        List<NexusBlock> result = new ArrayList<>();
        Iterator<NexusBlock> it = buffer.iterator();
        int count = 0;
        while (it.hasNext() && count < limit) {
            result.add(it.next());
            count++;
        }
        return result;
    }
}
