package com.paicli.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paicli.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史持久化存储。
 * 每次对话后自动保存，启动时自动加载上次的对话历史。
 * 滑动窗口：只保留最近 50 轮对话（user + assistant 共 100 条）。
 * 时间感知：记录每条消息的时间戳，加载时若间隔较长则注入时间提醒。
 */
public class ConversationHistoryStore {
    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    /** 50 轮对话 = 50 user + 50 assistant = 100 条消息 */
    private static final int MAX_TURNS = 50;
    private static final int MAX_MESSAGES = MAX_TURNS * 2;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 超过 30 分钟视为"间隔较长时间" */
    private static final long GAP_THRESHOLD_MINUTES = 30;

    private final Path storePath;

    public ConversationHistoryStore(Path projectPath) {
        this.storePath = projectPath.resolve(".paicli/conversations/last-session.json");
    }

    /**
     * 保存对话历史（滑动窗口，只保留最近 50 轮）
     */
    public void save(List<LlmClient.Message> history) {
        try {
            Files.createDirectories(storePath.getParent());
            List<StoredMessage> messages = new ArrayList<>();
            String now = LocalDateTime.now().format(FORMATTER);
            for (LlmClient.Message msg : history) {
                if (!"user".equals(msg.role()) && !"assistant".equals(msg.role())) {
                    continue;
                }
                if (msg.content() == null || msg.content().isBlank()) {
                    continue;
                }
                messages.add(new StoredMessage(msg.role(), msg.content(), now));
            }
            // 滑动窗口：只保留最近 MAX_TURNS 轮
            if (messages.size() > MAX_MESSAGES) {
                messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
            }
            MAPPER.writeValue(storePath.toFile(), messages);
            log.debug("Saved {} conversation messages to {}", messages.size(), storePath);
        } catch (IOException e) {
            log.warn("Failed to save conversation history", e);
        }
    }

    /**
     * 加载上次的对话历史，并返回时间感知上下文消息（如果有间隔）
     */
    public LoadResult load() {
        if (!Files.isReadable(storePath)) {
            return new LoadResult(List.of(), null);
        }
        try {
            List<StoredMessage> stored = MAPPER.readValue(
                    storePath.toFile(),
                    new TypeReference<List<StoredMessage>>() {});
            List<LlmClient.Message> messages = new ArrayList<>();
            String lastTimestamp = null;
            for (StoredMessage s : stored) {
                messages.add(new LlmClient.Message(s.role, s.content));
                if (s.timestamp != null) {
                    lastTimestamp = s.timestamp;
                }
            }
            // 计算时间间隔
            String timeGapMessage = buildTimeGapMessage(lastTimestamp);
            log.info("Loaded {} conversation messages from {}", messages.size(), storePath);
            return new LoadResult(messages, timeGapMessage);
        } catch (IOException e) {
            log.warn("Failed to load conversation history", e);
            return new LoadResult(List.of(), null);
        }
    }

    /**
     * 如果距离上次对话超过阈值，生成一条时间感知提示
     */
    private String buildTimeGapMessage(String lastTimestamp) {
        if (lastTimestamp == null) {
            return null;
        }
        try {
            LocalDateTime lastTime = LocalDateTime.parse(lastTimestamp, FORMATTER);
            LocalDateTime now = LocalDateTime.now();
            Duration gap = Duration.between(lastTime, now);
            long minutes = gap.toMinutes();
            if (minutes < GAP_THRESHOLD_MINUTES) {
                return null;
            }
            String gapDesc;
            if (minutes < 60) {
                gapDesc = minutes + " 分钟";
            } else {
                long hours = minutes / 60;
                long remainMinutes = minutes % 60;
                if (remainMinutes == 0) {
                    gapDesc = hours + " 小时";
                } else {
                    gapDesc = hours + " 小时 " + remainMinutes + " 分钟";
                }
            }
            return "距离上次对话已过去 " + gapDesc
                    + "（上次对话时间: " + lastTimestamp + "）。"
                    + "请根据当前时间调整回应，不要停留在上次的时间。";
        } catch (Exception e) {
            log.warn("Failed to parse last timestamp: {}", lastTimestamp, e);
            return null;
        }
    }

    /** 加载结果 */
    public record LoadResult(List<LlmClient.Message> messages, String timeGapMessage) {}

    /** 用于 JSON 序列化的消息记录（含时间戳） */
    @SuppressWarnings("unused")
    private static class StoredMessage {
        public String role;
        public String content;
        public String timestamp;

        public StoredMessage() {}

        public StoredMessage(String role, String content, String timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}
