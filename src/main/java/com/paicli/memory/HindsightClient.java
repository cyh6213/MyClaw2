package com.paicli.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HindsightClient {
    private static final Logger log = LoggerFactory.getLogger(HindsightClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String bankId;

    public HindsightClient(String baseUrl, String bankId) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.bankId = bankId;
    }

    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/health")
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.debug("Hindsight health check failed: {}", e.getMessage());
            return false;
        }
    }

    public void retain(String message) throws IOException {
        retain(message, null, null);
    }

    public void retain(String message, String context, List<String> tags) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories";
        java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("content", message);
        fields.put("timestamp", java.time.Instant.now().toString());
        if (context != null && !context.isBlank()) {
            fields.put("context", context);
        }
        if (tags != null && !tags.isEmpty()) {
            fields.put("tags", tags);
        }
        String json = mapper.writeValueAsString(Map.of("items", List.of(fields)));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight retain failed: " + response.code() + " - " + errorBody);
            }
            log.debug("Hindsight retain success: tags={}, context={}", tags, context);
        }
    }

    public void retain(List<Map<String, String>> messages) throws IOException {
        retain(messages, null, null);
    }

    public void retainConversation(String userMessage, String assistantMessage) throws IOException {
        retainConversation(userMessage, assistantMessage, null, null, null);
    }

    public void retainConversation(String userMessage, String assistantMessage,
                                    String context, List<String> tags,
                                    String documentId) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories";
        String now = java.time.Instant.now().toString();
        String todayDoc = documentId != null ? documentId : "chat-" + java.time.LocalDate.now().toString();

        // 把用户说和 AI 回的合并为一条 content，避免同一批请求里 document_id 重复
        String combined = "";
        if (userMessage != null && !userMessage.isBlank()) {
            combined = "用户说：" + userMessage;
        }
        if (assistantMessage != null && !assistantMessage.isBlank()) {
            if (!combined.isEmpty()) combined += "\n";
            combined += "角色回答：" + assistantMessage;
        }
        if (combined.isEmpty()) return;

        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("content", combined);
        item.put("timestamp", now);
        item.put("document_id", todayDoc);
        item.put("update_mode", "append");
        if (context != null && !context.isBlank()) {
            item.put("context", context);
        }
        if (tags != null && !tags.isEmpty()) {
            item.put("tags", tags);
        }

        String json = mapper.writeValueAsString(Map.of("items", List.of(item)));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight retain failed: " + response.code() + " - " + errorBody);
            }
            log.debug("Hindsight retain conversation: doc={}, tags={}, context={}", todayDoc, tags, context);
        }
    }

    public void retain(List<Map<String, String>> messages, String context, List<String> tags) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories";
        String now = java.time.Instant.now().toString();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("content", msg.getOrDefault("content", ""));
            item.put("timestamp", now);
            if (context != null && !context.isBlank()) {
                item.put("context", context);
            }
            if (tags != null && !tags.isEmpty()) {
                item.put("tags", tags);
            }
            items.add(item);
        }
        String json = mapper.writeValueAsString(Map.of("items", items));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight retain failed: " + response.code() + " - " + errorBody);
            }
            log.debug("Hindsight retain success: {} items, tags={}", items.size(), tags);
        }
    }

    public List<MemoryEntry> recall(String query, int limit) throws IOException {
        return recall(query, limit, 600);
    }

    public List<MemoryEntry> recall(String query, int limit, int maxTokens) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories/recall";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("query", query);
        body.put("budget", "low");
        body.put("max_tokens", Math.max(256, Math.min(maxTokens, 800)));
        String json = mapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight recall failed: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode root = mapper.readTree(responseBody);
            // Hindsight API 返回 results 字段，不是 memories
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                results = root.get("memories");
            }

            List<MemoryEntry> entries = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    String text = item.has("text") ? item.get("text").asText() : "";
                    String type = item.has("type") ? item.get("type").asText() : "experience";
                    String id = item.has("id") ? item.get("id").asText() : "";

                    if (!text.isEmpty()) {
                        MemoryEntry.MemoryType entryType = "observation".equals(type)
                                ? MemoryEntry.MemoryType.SUMMARY
                                : MemoryEntry.MemoryType.FACT;

                        entries.add(new MemoryEntry(
                                "hindsight-" + (id.isEmpty() ? String.valueOf(System.currentTimeMillis()) : id),
                                text,
                                entryType,
                                Map.of("source", "hindsight", "hindsight_type", type),
                                MemoryEntry.estimateTokens(text)
                        ));
                    }
                }
            }
            log.debug("Hindsight recall returned {} memories", entries.size());
            return entries;
        }
    }

    public String reflect(String query) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/reflect";
        Map<String, Object> body = Map.of(
                "query", query,
                "budget", "low",
                "max_tokens", 2048
        );
        String json = mapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight reflect failed: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode root = mapper.readTree(responseBody);
            return root.has("reflection") ? root.get("reflection").asText() : "";
        }
    }

    public List<MemoryEntry> listMemories() throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight list memories failed: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode root = mapper.readTree(responseBody);
            JsonNode memories = root.get("memories");

            List<MemoryEntry> entries = new ArrayList<>();
            if (memories != null && memories.isArray()) {
                for (JsonNode memory : memories) {
                    String text = memory.has("text") ? memory.get("text").asText() : "";
                    String type = memory.has("type") ? memory.get("type").asText() : "experience";
                    String id = memory.has("id") ? memory.get("id").asText() : "";

                    if (!text.isEmpty()) {
                        MemoryEntry.MemoryType entryType = "observation".equals(type)
                                ? MemoryEntry.MemoryType.SUMMARY
                                : MemoryEntry.MemoryType.FACT;

                        entries.add(new MemoryEntry(
                                "hindsight-" + (id.isEmpty() ? String.valueOf(System.currentTimeMillis()) : id),
                                text,
                                entryType,
                                Map.of("source", "hindsight", "hindsight_type", type),
                                MemoryEntry.estimateTokens(text)
                        ));
                    }
                }
            }
            return entries;
        }
    }

    public boolean deleteMemory(String id) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories/" + id;

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    public void clearMemories() throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories";

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight clear memories failed: " + response.code() + " - " + errorBody);
            }
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getBankId() {
        return bankId;
    }
}
