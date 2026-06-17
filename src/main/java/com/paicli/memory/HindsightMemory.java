package com.paicli.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HindsightMemory implements Memory {
    private static final Logger log = LoggerFactory.getLogger(HindsightMemory.class);

    private final HindsightClient client;
    private final AtomicInteger tokenCounter;

    public HindsightMemory(HindsightClient client) {
        this.client = client;
        this.tokenCounter = new AtomicInteger(0);
    }

    @Override
    public void store(MemoryEntry entry) {
        try {
            client.retain(entry.getContent());
            tokenCounter.addAndGet(entry.getTokenCount());
            log.debug("Stored memory to Hindsight: {}", entry.getContent());
        } catch (IOException e) {
            log.warn("Failed to store memory to Hindsight: {}", e.getMessage());
        }
    }

    public void storeConversation(String userMessage, String assistantMessage) {
        try {
            client.retainConversation(
                    userMessage, assistantMessage,
                    "chat",
                    List.of("conversation"),
                    null
            );
            tokenCounter.addAndGet(MemoryEntry.estimateTokens(userMessage) + MemoryEntry.estimateTokens(assistantMessage));
            log.debug("Stored conversation to Hindsight");
        } catch (IOException e) {
            log.debug("Failed to store conversation to Hindsight: {}", e.getMessage());
        }
    }

    @Override
    public Optional<MemoryEntry> retrieve(String id) {
        try {
            List<MemoryEntry> all = getAll();
            return all.stream()
                    .filter(entry -> entry.getId().equals(id))
                    .findFirst();
        } catch (Exception e) {
            log.warn("Failed to retrieve memory from Hindsight: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<MemoryEntry> search(String query, int limit) {
        return search(query, limit, null);
    }

    public List<MemoryEntry> search(String query, int limit, String projectKey) {
        try {
            List<MemoryEntry> results = client.recall(query, limit);
            if (projectKey != null && !projectKey.isBlank()) {
                return results.stream()
                        .filter(entry -> isVisibleInProject(entry, projectKey))
                        .collect(Collectors.toList());
            }
            return results;
        } catch (IOException e) {
            log.warn("Failed to search memory in Hindsight: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<MemoryEntry> getAll() {
        try {
            return client.listMemories();
        } catch (IOException e) {
            log.warn("Failed to get all memories from Hindsight: {}", e.getMessage());
            return List.of();
        }
    }

    public List<MemoryEntry> getAll(String projectKey) {
        return getAll().stream()
                .filter(entry -> isVisibleInProject(entry, projectKey))
                .collect(Collectors.toList());
    }

    @Override
    public boolean delete(String id) {
        try {
            String hindsightId = id.startsWith("hindsight-") ? id.substring("hindsight-".length()) : id;
            boolean success = client.deleteMemory(hindsightId);
            if (success) {
                tokenCounter.addAndGet(-10);
            }
            return success;
        } catch (IOException e) {
            log.warn("Failed to delete memory from Hindsight: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            client.clearMemories();
            tokenCounter.set(0);
        } catch (IOException e) {
            log.warn("Failed to clear memories from Hindsight: {}", e.getMessage());
        }
    }

    @Override
    public int getTokenCount() {
        return tokenCounter.get();
    }

    @Override
    public int size() {
        return getAll().size();
    }

    public String reflect(String query) {
        try {
            return client.reflect(query);
        } catch (IOException e) {
            log.warn("Failed to reflect in Hindsight: {}", e.getMessage());
            return "";
        }
    }

    public static boolean isVisibleInProject(MemoryEntry entry, String projectKey) {
        String scope = entry.getMetadata().get("scope");
        if ("global".equals(scope)) {
            return true;
        }
        return true;
    }

    public static String scopeOf(MemoryEntry entry) {
        String scope = entry.getMetadata().get("scope");
        if ("project".equalsIgnoreCase(scope)) {
            return "project";
        }
        return "global";
    }

    public HindsightClient getClient() {
        return client;
    }

    public String getStatusSummary() {
        return "长期记忆(Hindsight): " + size() + "条 / " + getTokenCount() + " tokens";
    }
}
