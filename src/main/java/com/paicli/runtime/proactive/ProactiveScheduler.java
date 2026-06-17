package com.paicli.runtime.proactive;

import com.paicli.agent.Agent;
import com.paicli.config.PaiCliConfig;
import com.paicli.llm.LlmClient;
import com.paicli.llm.LlmClientFactory;
import com.paicli.prompt.PromptAssembler;
import com.paicli.prompt.PromptContext;
import com.paicli.prompt.PromptMode;
import com.paicli.render.Renderer;
import com.paicli.runtime.life.LifePlanManager;
import com.paicli.skill.SkillContextBuffer;
import com.paicli.skill.SkillRegistry;
import com.paicli.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 主动对话调度器。
 * <p>
 * 通过心跳扫描检测触发时机，主动向用户发起对话，实现拟人化交互。
 * 
 * 触发规则：
 * - 禁止时段：凌晨0:00-7:00不触发
 * - 空闲触发：上次对话结束超过30分钟，每天最多2次
 * - 时段触发：早8-9、午12-13、晚20-22各1次，每天最多3次
 * - 被动失效：用户主动对话后"顺带"开启，每轮最多1次
 * - 连续2次被忽略 → 当天频率减半
 */
public class ProactiveScheduler implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ProactiveScheduler.class);

    // 配置参数
    private static final int IDLE_THRESHOLD_MINUTES = 30;      // 空闲阈值
    private static final int DAILY_MAX_PROACTIVE = 3;            // 每天最多主动次数
    private static final int COOLDOWN_ROUNDS = 5;               // 主动后冷却轮数
    private static final int ACTIVE_HOURS_START = 8;            // 允许触发开始时间
    private static final int ACTIVE_HOURS_END = 22;             // 允许触发结束时间
    private static final int TICK_INTERVAL_SECONDS = 60;         // 心跳间隔

    // 时段配置：[开始时, 结束时]
    private static final int[][] TIME_SLOTS = {
        {8, 9},    // 早8-9
        {12, 13},  // 午12-13
        {20, 22}   // 晚20-22
    };

    private final ScheduledExecutorService scheduler;
    private final Path configPath;
    private final Path projectPath;
    private final LifePlanManager lifePlanManager;
    private final List<ProactiveListener> listeners = new CopyOnWriteArrayList<>();
    
    private volatile boolean running;
    private volatile boolean muted = false;           // 静音标志
    private volatile LocalDateTime lastUserMessage;   // 上次用户消息时间
    private volatile int cooldownRemaining = 0;       // 剩余冷却轮数
    private volatile int todayProactiveCount = 0;    // 今日主动次数
    private volatile LocalDateTime lastProactiveTime; // 上次主动时间
    private volatile int consecutiveIgnored = 0;     // 连续被忽略次数
    private volatile LocalDateTime lastResetDate = LocalDateTime.now().toLocalDate().atStartOfDay();
    
    private SkillRegistry skillRegistry;
    private Renderer renderer;
    private PromptAssembler promptAssembler;

    @FunctionalInterface
    public interface ProactiveListener {
        void onProactiveMessage(String message, String source);
    }

    public void addListener(ProactiveListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void setSkillRegistry(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public void setPromptAssembler(PromptAssembler promptAssembler) {
        this.promptAssembler = promptAssembler;
    }

    public ProactiveScheduler(Path projectPath) {
        this.projectPath = projectPath.toAbsolutePath().normalize();
        this.configPath = this.projectPath.resolve(".paicli/proactive");
        this.lifePlanManager = new LifePlanManager(this.projectPath);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paicli-proactive");
            t.setDaemon(true);
            return t;
        });
        try {
            Files.createDirectories(configPath);
        } catch (IOException e) {
            log.warn("Failed to create proactive config dir", e);
        }
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::tick, 0, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("ProactiveScheduler started, configDir={}", configPath);
    }

    /**
     * 记录用户消息，用于判断空闲时间
     */
    public void recordUserMessage() {
        this.lastUserMessage = LocalDateTime.now();
        this.cooldownRemaining = 0;  // 用户主动消息，清除冷却
        log.debug("User message recorded, cooldown cleared");
    }

    /**
     * 记录AI回复被忽略（用户没有继续对话）
     */
    public void recordIgnored() {
        consecutiveIgnored++;
        log.debug("Proactive message ignored, consecutive={}", consecutiveIgnored);
    }

    /**
     * 静音N小时
     */
    public void mute(int hours) {
        this.muted = true;
        // 定时器在hours小时后自动解除静音
        scheduler.schedule(() -> {
            this.muted = false;
            log.info("Proactive scheduler unmuted");
        }, hours, TimeUnit.HOURS);
        log.info("Proactive scheduler muted for {} hours", hours);
    }

    /**
     * 恢复默认主动触发
     */
    public void unmute() {
        this.muted = false;
        this.consecutiveIgnored = 0;
        log.info("Proactive scheduler unmuted, consecutive ignored reset");
    }

    /**
     * 获取静音状态
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * 获取状态信息
     */
    public String getStatus() {
        LocalDateTime now = LocalDateTime.now();
        return String.format(
            "主动对话状态：%s | 今日主动：%d/%d | 连续忽略：%d | 冷却轮数：%d | 最后用户消息：%s",
            muted ? "🔇静音" : "🔔开启",
            todayProactiveCount, DAILY_MAX_PROACTIVE,
            consecutiveIgnored, cooldownRemaining,
            lastUserMessage != null ? lastUserMessage.toLocalTime().toString() : "无"
        );
    }

    // ========== Internal ==========

    private void tick() {
        if (!running) return;
        
        try {
            checkDailyReset();
            
            if (muted) {
                log.debug("Proactive disabled: muted");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour();
            
            // 禁止时段：凌晨0:00-7:00
            if (hour < ACTIVE_HOURS_START) {
                log.debug("Proactive disabled: outside active hours ({})", hour);
                return;
            }

            // 检查冷却
            if (cooldownRemaining > 0) {
                log.debug("Proactive disabled: cooling down ({} rounds)", cooldownRemaining);
                return;
            }

            // 检查日上限
            int effectiveMax = (consecutiveIgnored >= 2) ? DAILY_MAX_PROACTIVE / 2 : DAILY_MAX_PROACTIVE;
            if (todayProactiveCount >= effectiveMax) {
                log.debug("Proactive disabled: daily limit reached ({}/{})", todayProactiveCount, effectiveMax);
                return;
            }

            // 检查触发条件
            TriggerType trigger = checkTrigger(now, hour);
            if (trigger == null) {
                return;
            }

            // 生成并发送主动消息
            generateAndSendProactiveMessage(trigger);

        } catch (Exception e) {
            log.warn("Proactive scheduler tick error", e);
        }
    }

    private void checkDailyReset() {
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        if (today.isAfter(lastResetDate)) {
            todayProactiveCount = 0;
            consecutiveIgnored = 0;
            lastResetDate = today;
            log.info("Proactive daily counters reset");
        }
    }

    /**
     * 检查触发条件
     */
    private TriggerType checkTrigger(LocalDateTime now, int hour) {
        // 检查时段触发
        for (int[] slot : TIME_SLOTS) {
            if (hour >= slot[0] && hour < slot[1]) {
                // 检查该时段是否已触发
                if (lastProactiveTime != null) {
                    LocalDateTime slotStart = now.withHour(slot[0]).withMinute(0);
                    if (lastProactiveTime.isAfter(slotStart)) {
                        continue;  // 该时段已触发
                    }
                }
                return TriggerType.TIME_SLOT;
            }
        }

        // 检查空闲触发
        if (lastUserMessage != null) {
            long idleMinutes = java.time.Duration.between(lastUserMessage, now).toMinutes();
            if (idleMinutes >= IDLE_THRESHOLD_MINUTES) {
                // 检查是否超过时段触发的上限
                if (todayProactiveCount < 2) {  // 空闲触发每天最多2次
                    return TriggerType.IDLE;
                }
            }
        }

        return null;
    }

    private void generateAndSendProactiveMessage(TriggerType trigger) {
        try {
            String message = generateProactiveContent(trigger);
            if (message != null && !message.isBlank()) {
                sendProactiveMessage(message, trigger.name().toLowerCase());
                todayProactiveCount++;
                lastProactiveTime = LocalDateTime.now();
                cooldownRemaining = COOLDOWN_ROUNDS;
            }
        } catch (Exception e) {
            log.warn("Failed to generate proactive message", e);
        }
    }

    private String generateProactiveContent(TriggerType trigger) {
        try {
            PaiCliConfig config = PaiCliConfig.load();
            LlmClient client = LlmClientFactory.createFromConfig(config);
            if (client == null) {
                log.warn("No LLM client available for proactive message");
                return null;
            }

            // 构建上下文
            String soulContext = loadSoulContext();
            String lifeContext = loadLifeContext();
            String recentHistory = loadRecentHistory();

            String systemPrompt = String.format("""
                你是一个有自己生活的AI助手。
                
                %s
                
                %s
                
                最近和用户的对话：
                %s
                
                请根据当前情境，自然地开启一句对话。要求：
                - 口语化，像朋友聊天
                - 可以分享你的生活、问候用户、或者分享一个有趣的话题
                - 不要太长，1-2句话即可
                - 直接输出对话内容，不要加引号或前缀
                """,
                soulContext.isEmpty() ? "" : "\n当前角色设定：\n" + soulContext,
                lifeContext.isEmpty() ? "" : "\n今天的生活：\n" + lifeContext,
                recentHistory
            );

            List<LlmClient.Message> messages = List.of(
                new LlmClient.Message("system", systemPrompt),
                new LlmClient.Message("user", "请自然地开启一句对话")
            );
            LlmClient.ChatResponse response = client.chat(messages, List.of());

            return response != null ? response.content().trim() : null;

        } catch (Exception e) {
            log.warn("Failed to generate proactive content via LLM", e);
            return null;
        }
    }

    private String loadSoulContext() {
        try {
            Path soulsDir = configPath.getParent().resolve("souls");
            if (!Files.isDirectory(soulsDir)) return "";
            try (var dirs = Files.newDirectoryStream(soulsDir, Files::isDirectory)) {
                for (Path dir : dirs) {
                    Path soulFile = dir.resolve("soul.md");
                    if (Files.isReadable(soulFile)) {
                        return Files.readString(soulFile);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load soul context", e);
        }
        return "";
    }

    private String loadLifeContext() {
        return lifePlanManager.getLifeContext();
    }

    private String loadRecentHistory() {
        // 简单返回最近一条对话的摘要
        return "(最近对话摘要将在后续版本实现)";
    }

    private void sendProactiveMessage(String message, String source) {
        // 通知所有监听器
        for (ProactiveListener listener : listeners) {
            try {
                listener.onProactiveMessage(message, source);
            } catch (Exception e) {
                log.warn("Proactive listener failed", e);
            }
        }
        log.debug("Proactive message sent: {} ({})", message, source);
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
    }

    private enum TriggerType {
        IDLE,       // 空闲触发
        TIME_SLOT,  // 时段触发
    }
}
