package com.paicli.memory;

import com.paicli.llm.LlmClient;
import com.paicli.context.ContextProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memory 管理器 - Memory 系统的门面类
 *
 * 统一管理短期记忆、长期记忆、上下文压缩和检索，
 * 为 Agent 提供简洁的记忆存取接口。
 *
 * 支持两种长期记忆实现：
 * - 本地文件存储（LongTermMemory）
 * - Hindsight 向量+图数据库（HindsightMemory）
 */
public class MemoryManager {
    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);
    private final ConversationMemory shortTermMemory;
    private final Memory longTermMemory;
    private final ContextCompressor compressor;
    private final MemoryRetriever retriever;
    private TokenBudget tokenBudget;
    private ContextProfile contextProfile;
    private final ExecutorService retainExecutor;
    private String currentProject;

    public MemoryManager(LlmClient llmClient) {
        this(llmClient, ContextProfile.from(llmClient), null);
    }

    /**
     * @param llmClient      LLM 客户端（用于压缩时的摘要生成）
     * @param shortTermBudget 短期记忆 token 预算
     * @param contextWindow  模型上下文窗口大小
     */
    public MemoryManager(LlmClient llmClient, int shortTermBudget, int contextWindow) {
        this(llmClient, shortTermBudget, contextWindow, null);
    }

    public MemoryManager(LlmClient llmClient, int shortTermBudget, int contextWindow, Memory longTermMemory) {
        this(llmClient, ContextProfile.custom(contextWindow, shortTermBudget), longTermMemory);
    }

    public MemoryManager(LlmClient llmClient, ContextProfile contextProfile, Memory longTermMemory) {
        this.contextProfile = contextProfile;
        this.shortTermMemory = new ConversationMemory(contextProfile.shortTermMemoryBudget());
        this.longTermMemory = longTermMemory != null ? longTermMemory : new LongTermMemory();
        this.compressor = new ContextCompressor(llmClient);
        this.retriever = new MemoryRetriever(shortTermMemory, this.longTermMemory);
        this.tokenBudget = new TokenBudget(contextProfile.maxContextWindow());
        this.currentProject = defaultProjectKey();
        this.retainExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hindsight-retain-thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void setLlmClient(LlmClient llmClient) {
        this.compressor.setLlmClient(llmClient);
        applyContextProfile(ContextProfile.from(llmClient));
    }

    public void applyContextProfile(ContextProfile contextProfile) {
        this.contextProfile = contextProfile;
        this.tokenBudget = new TokenBudget(contextProfile.maxContextWindow());
        this.shortTermMemory.setMaxTokens(contextProfile.shortTermMemoryBudget());
    }

    public void setProjectPath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }
        this.currentProject = normalizeProjectKey(projectPath);
    }

    /**
     * 添加用户消息到短期记忆
     */
    public void addUserMessage(String content) {
        MemoryEntry entry = new MemoryEntry(
                "user-" + UUID.randomUUID().toString().substring(0, 8),
                content,
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", "user"),
                MemoryEntry.estimateTokens(content)
        );
        shortTermMemory.store(entry);
        compressIfNeeded();
    }

    /**
     * 添加助手回复到短期记忆
     */
    public void addAssistantMessage(String content) {
        MemoryEntry entry = new MemoryEntry(
                "assistant-" + UUID.randomUUID().toString().substring(0, 8),
                content,
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", "assistant"),
                MemoryEntry.estimateTokens(content)
        );
        shortTermMemory.store(entry);
        compressIfNeeded();
    }

    /**
     * 将整轮对话 retain 到 Hindsight（异步执行，不阻塞）
     * 在 Agent 每轮对话完成后调用
     */
    public void retainConversation(String userMessage, String assistantMessage) {
        if (longTermMemory instanceof HindsightMemory hindsightMemory) {
            retainExecutor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    hindsightMemory.storeConversation(userMessage, assistantMessage);
                    log.debug("Hindsight retain completed in {} ms", System.currentTimeMillis() - start);
                } catch (Exception e) {
                    log.warn("Hindsight retain failed (async): {}", e.getMessage());
                }
            });
        }
    }

    // 工具结果在记忆中的最大长度（完整结果已在任务消息历史里，记忆只需保留摘要）
    private static final int MAX_TOOL_RESULT_CHARS = 500;

    /**
     * 添加工具执行结果到短期记忆（截断过长结果，避免快速撑满预算）
     */
    public void addToolResult(String toolName, String result) {
        String truncated = result.length() > MAX_TOOL_RESULT_CHARS
                ? result.substring(0, MAX_TOOL_RESULT_CHARS) + "...(已截断)"
                : result;
        String content = "[" + toolName + "] " + truncated;
        MemoryEntry entry = new MemoryEntry(
                "tool-" + UUID.randomUUID().toString().substring(0, 8),
                content,
                MemoryEntry.MemoryType.TOOL_RESULT,
                Map.of("source", "tool", "toolName", toolName),
                MemoryEntry.estimateTokens(content)
        );
        shortTermMemory.store(entry);
        compressIfNeeded();
    }

    /**
     * 存储关键事实到长期记忆
     */
    public void storeFact(String fact) {
        storeFact(fact, "project");
    }

    public void storeFact(String fact, String scope) {
        String normalizedScope = normalizeScope(scope);
        Map<String, String> metadata = "global".equals(normalizedScope)
                ? Map.of("source", "fact", "scope", "global")
                : Map.of("source", "fact", "scope", "project", "project", currentProject);
        MemoryEntry entry = new MemoryEntry(
                "fact-" + UUID.randomUUID().toString().substring(0, 8),
                fact,
                MemoryEntry.MemoryType.FACT,
                metadata,
                MemoryEntry.estimateTokens(fact)
        );
        longTermMemory.store(entry);
    }

    /**
     * 检索与查询最相关的记忆
     */
    public List<MemoryEntry> retrieveRelevant(String query, int limit) {
        return retriever.retrieve(query, limit);
    }

    public List<MemoryEntry> listLongTerm() {
        return longTermMemory.getAll();
    }

    public List<MemoryEntry> searchLongTerm(String query, int limit) {
        if (longTermMemory instanceof LongTermMemory) {
            return ((LongTermMemory) longTermMemory).search(query, limit, currentProject);
        } else if (longTermMemory instanceof HindsightMemory) {
            return ((HindsightMemory) longTermMemory).search(query, limit, currentProject);
        }
        return longTermMemory.search(query, limit);
    }

    public boolean deleteLongTerm(String id) {
        return longTermMemory.delete(id);
    }

    /**
     * 构建用于 LLM 的记忆上下文
     * 如果启用了 Hindsight，优先使用 Hindsight recall（语义检索）
     * 否则回退到本地关键词匹配
     */
    public String buildContextForQuery(String query, int maxTokens) {
        log.debug("buildContextForQuery called, isUsingHindsight={}, query='{}'", isUsingHindsight(), query);
        if (longTermMemory instanceof HindsightMemory hindsightMemory) {
            try {
                List<MemoryEntry> recalled = hindsightMemory.getClient().recall(query, 10, maxTokens);
                log.debug("Hindsight recall returned {} memories", recalled.size());
                if (recalled.isEmpty()) {
                    return "";
                }

                StringBuilder context = new StringBuilder();
                context.append("## 相关长期记忆\n\n");
                int usedTokens = 0;
                for (MemoryEntry entry : recalled) {
                    if (usedTokens + entry.getTokenCount() > maxTokens) break;
                    context.append("- [").append(entry.getType()).append("] ")
                            .append(entry.getContent()).append("\n");
                    usedTokens += entry.getTokenCount();
                }
                context.append("\n");
                log.debug("Hindsight recall retrieved {} memories for query '{}', injected {} chars",
                        recalled.size(), query, context.length());
                return context.toString();
            } catch (Exception e) {
                log.warn("Hindsight recall failed, fallback to local: {}", e.getMessage());
            }
        }
        log.info("Using local retriever for query '{}'", query);
        return retriever.buildContextForQuery(query, maxTokens, currentProject);
    }

    public boolean isUsingHindsight() {
        return longTermMemory instanceof HindsightMemory;
    }

    public HindsightMemory getHindsightMemory() {
        return isUsingHindsight() ? (HindsightMemory) longTermMemory : null;
    }

    /**
     * 记录 token 使用
     */
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens);
    }

    public void recordTokenUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens, cachedInputTokens);
    }

    /**
     * 检查并触发压缩（由 Agent 在 LLM 调用前主动调用）
     *
     * @return 是否执行了压缩
     */
    public boolean compressIfNeeded() {
        // 压缩永远可触发，模式概念已删除。触发条件仅看占用率是否到达 ContextProfile 配置的自动压缩阈值。
        if (!tokenBudget.needsCompression(shortTermMemory, contextProfile.compressionTriggerRatio())) {
            return false;
        }
        int beforeTokens = shortTermMemory.getTokenCount();
        log.info("上下文占用达到压缩阈值（{}%），触发短期记忆压缩",
                (int) (contextProfile.compressionTriggerRatio() * 100));
        String summary = compressor.compress(shortTermMemory);
        if (summary != null) {
            int afterTokens = shortTermMemory.getTokenCount();
            String preview = summary.substring(0, Math.min(100, summary.length()));
            log.info("短期记忆压缩完成: {} -> {} tokens, summaryPreview={}", beforeTokens, afterTokens, preview);
        }
        return summary != null;
    }

    /**
     * 清空短期记忆（保留长期记忆）
     */
    public void clearShortTerm() {
        shortTermMemory.clear();
    }

    /**
     * 清空长期记忆
     */
    public void clearLongTerm() {
        longTermMemory.clear();
    }

    /**
     * 获取记忆系统的整体状态
     */
    public String getSystemStatus() {
        String longTermStatus;
        if (longTermMemory instanceof LongTermMemory) {
            longTermStatus = ((LongTermMemory) longTermMemory).getStatusSummary();
        } else if (longTermMemory instanceof HindsightMemory) {
            longTermStatus = ((HindsightMemory) longTermMemory).getStatusSummary();
        } else {
            longTermStatus = "长期记忆: " + longTermMemory.size() + "条 / " + longTermMemory.getTokenCount() + " tokens";
        }
        return "上下文策略: " + contextProfile.summary() + "\n" +
                shortTermMemory.getStatusSummary() + "\n" +
                longTermStatus + "\n" +
                tokenBudget.getUsageReport();
    }

    // Getter
    public ConversationMemory getShortTermMemory() { return shortTermMemory; }
    public Memory getLongTermMemory() { return longTermMemory; }
    public TokenBudget getTokenBudget() { return tokenBudget; }
    public ContextProfile getContextProfile() { return contextProfile; }

    public String getCurrentProject() { return currentProject; }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "project";
        }
        String normalized = scope.trim().toLowerCase();
        return "global".equals(normalized) ? "global" : "project";
    }

    private static String defaultProjectKey() {
        return normalizeProjectKey(System.getProperty("user.dir"));
    }

    private static String normalizeProjectKey(String path) {
        try {
            Path candidate = Path.of(path).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toRealPath().toString();
            }
            return candidate.toString();
        } catch (Exception e) {
            return Path.of(path).toAbsolutePath().normalize().toString();
        }
    }
}
