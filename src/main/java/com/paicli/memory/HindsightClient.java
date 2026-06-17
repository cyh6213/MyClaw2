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
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
        retain(List.of(Map.of("role", "user", "content", message)));
    }

    public void retain(List<Map<String, String>> messages) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories";
        Map<String, Object> body = Map.of("items", messages);
        String json = mapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Hindsight retain failed: " + response.code() + " - " + errorBody);
            }
            log.debug("Hindsight retain success");
        }
    }

    public List<MemoryEntry> recall(String query, int limit) throws IOException {
        String url = baseUrl + "/v1/default/banks/" + bankId + "/memories/recall";
        Map<String, Object> body = Map.of(
                "query", query,
                "limit", limit
        );
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
