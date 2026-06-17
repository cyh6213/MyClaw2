package com.paicli.runtime.task;

import com.paicli.agent.Agent;
import com.paicli.config.PaiCliConfig;
import com.paicli.llm.LlmClient;
import com.paicli.llm.LlmClientFactory;
import com.paicli.prompt.PromptMode;
import com.paicli.render.Renderer;
import com.paicli.skill.SkillContextBuffer;
import com.paicli.skill.SkillRegistry;
import com.paicli.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 定时任务管理器。
 * <p>
 * 任务以 .task 文件存储在 .paicli/tasks/ 下，持久化、可读写。
 * 支持 daily（每日循环）和 once（一次性）两种类型。
 * 每 15 秒 tick 检查到点任务，执行后通知监听器。
 */
public class ScheduledTaskManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskManager.class);

    private final Path tasksDir;
    private final ScheduledExecutorService scheduler;
    private final List<TaskResultListener> listeners = new CopyOnWriteArrayList<>();
    private final Path projectPath;
    private SkillRegistry skillRegistry;
    private Renderer renderer;
    private volatile boolean running;

    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    @FunctionalInterface
    public interface TaskResultListener {
        void onTaskResult(String taskId, String result, String prompt);
    }

    public void addListener(TaskResultListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void setSkillRegistry(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public ScheduledTaskManager(Path projectPath) throws IOException {
        this.projectPath = projectPath.toAbsolutePath().normalize();
        this.tasksDir = this.projectPath.resolve(".paicli/tasks");
        Files.createDirectories(tasksDir);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paicli-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::tick, 0, 15, TimeUnit.SECONDS);
        log.info("ScheduledTaskManager started, tasksDir={}", tasksDir);
    }

    // ========== CRUD ==========

    /** 添加每日循环任务，time 格式 "HH:mm" */
    public Task addDailyTask(String id, String prompt, String time) throws IOException {
        // 检查任务ID是否已存在
        if (Files.exists(tasksDir.resolve(id + ".task"))) {
            throw new IOException("任务ID已存在: " + id);
        }
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute));
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        Task task = new Task(id, "daily", prompt, hour, minute, nextRun, true);
        writeTask(task);
        log.info("Daily task added: id={}, time={}, next_run={}", id, time, nextRun);
        return task;
    }

    /** 添加一次性任务 */
    public Task addOneTimeTask(String id, String prompt, LocalDateTime scheduledAt) throws IOException {
        // 检查任务ID是否已存在
        if (Files.exists(tasksDir.resolve(id + ".task"))) {
            throw new IOException("任务ID已存在: " + id);
        }
        Task task = new Task(id, "once", prompt, 0, 0, scheduledAt, false);
        writeTask(task);
        log.info("One-time task added: id={}, scheduled_at={}", id, scheduledAt);
        return task;
    }

    /** 立即执行指定任务 */
    public String runNow(String id) {
        Path file = tasksDir.resolve(id + ".task");
        if (!Files.exists(file)) return "未找到任务: " + id;
        try {
            Task task = readTask(file);
            if (task == null) return "任务文件解析失败: " + id;
            // 把 next_run 设成过去，下一个 tick 会触发
            Task updated = new Task(task.id(), task.type(), task.prompt(),
                    task.hour(), task.minute(), LocalDateTime.now().minusSeconds(1), task.system());
            writeTask(updated);
            return "✅ 任务 [" + id + "] 已标记为立即执行，将在 15 秒内触发。";
        } catch (IOException e) {
            return "执行失败: " + e.getMessage();
        }
    }

    /** 删除任务 */
    public boolean removeTask(String id) {
        Path file = tasksDir.resolve(id + ".task");
        return file.toFile().delete();
    }

    /** 列出所有任务 */
    public List<Task> listTasks() {
        List<Task> tasks = new ArrayList<>();
        Path dir = tasksDir;
        if (!Files.isDirectory(dir)) return tasks;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".task"))
                    .forEach(p -> {
                        try {
                            Task t = readTask(p);
                            if (t != null) tasks.add(t);
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}
        return tasks;
    }

    public Path tasksDir() {
        return tasksDir;
    }

    // ========== Internal ==========

    private void tick() {
        if (!running) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Task> dueTasks = new ArrayList<>();
            try (Stream<Path> files = Files.list(tasksDir)) {
                files.filter(p -> p.toString().endsWith(".task")).forEach(p -> {
                    try {
                        Task t = readTask(p);
                        if (t != null && !t.nextRun.isAfter(now)) {
                            dueTasks.add(t);
                        }
                    } catch (Exception ignored) {}
                });
            }
            for (Task task : dueTasks) {
                log.info("Task triggered: id={}, type={}", task.id, task.type);
                executeTask(task);
            }
        } catch (Exception e) {
            log.warn("Scheduler tick error", e);
        }
    }

    private void executeTask(Task task) {
        String result = "";
        if (task.system()) {
            // 系统任务（如 daily-worker）：调 LLM 执行（读写文件、编日常）
            try {
                PaiCliConfig config = PaiCliConfig.load();
                LlmClient client = LlmClientFactory.createFromConfig(config);
                if (client != null) {
                    ToolRegistry registry = new ToolRegistry();
                    registry.setProjectPath(projectPath.toString());
                    if (skillRegistry != null) {
                        registry.setSkillRegistry(skillRegistry);
                        registry.setSkillContextBuffer(new SkillContextBuffer());
                    }
                    Agent agent = new Agent(client, registry);
                    agent.setRenderer(renderer != null ? renderer : new com.paicli.render.PlainRenderer());
                    agent.setMode(PromptMode.MANAGE);
                    agent.setReturnFinalResponseWhenStreamed(true);
                    if (skillRegistry != null) {
                        agent.setSkillRegistry(skillRegistry);
                        agent.setSkillContextBuffer(new SkillContextBuffer());
                    }
                    String context = "当前时间 " + LocalDateTime.now() + "。" + task.prompt;
                    result = agent.run(context);
                    log.info("System task result: id={}, length={}", task.id, result == null ? 0 : result.length());
                }
            } catch (Exception e) {
                log.warn("System task execution failed: id=" + task.id, e);
                result = "执行失败: " + e.getMessage();
            }
        } else {
            // 用户创建的提醒：不需要调 LLM，直接把 prompt 当消息发送
            result = task.prompt;
        }

        // 处理后续
        String finalResult = result == null ? "" : result;
        try {
            if ("once".equals(task.type)) {
                removeTask(task.id);
                log.info("One-time task done and removed: id={}", task.id);
            } else {
                // 修复时间调度bug：保存当前时间用于比较，避免重复调用now()
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime next = now.withHour(task.hour).withMinute(task.minute).withSecond(0).withNano(0);
                if (!next.isAfter(now)) {
                    next = next.plusDays(1);
                }
                Task updated = new Task(task.id, task.type, task.prompt, task.hour, task.minute, next, true);
                writeTask(updated);
                log.info("Daily task rescheduled: id={}, next_run={}", task.id, next);
            }
        } catch (IOException e) {
            log.warn("Task post-processing failed: id=" + task.id, e);
        }

        // 通知监听器
        for (TaskResultListener listener : listeners) {
            try {
                listener.onTaskResult(task.id, finalResult, task.prompt);
            } catch (Exception e) {
                log.warn("Listener failed: id=" + task.id, e);
            }
        }
    }

    private void writeTask(Task task) throws IOException {
        Path file = tasksDir.resolve(task.id + ".task");
        String content = String.join("\n",
                "id=" + task.id,
                "type=" + task.type,
                "hour=" + task.hour,
                "minute=" + task.minute,
                "next_run=" + task.nextRun,
                "system=" + task.system,
                "---",
                task.prompt
        );
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private Task readTask(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String id = "", type = "daily", prompt = "";
        int hour = 0, minute = 0;
        String nextRun = "";
        boolean system = false;
        boolean inBody = false;
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (line.equals("---")) { inBody = true; continue; }
            if (inBody) { body.append(line).append("\n"); continue; }
            if (line.startsWith("id=")) id = line.substring(3);
            else if (line.startsWith("type=")) type = line.substring(5);
            else if (line.startsWith("hour=")) {
                try {
                    hour = Integer.parseInt(line.substring(5));
                } catch (NumberFormatException e) {
                    log.warn("Invalid hour format in task file: {}", file);
                }
            }
            else if (line.startsWith("minute=")) {
                try {
                    minute = Integer.parseInt(line.substring(7));
                } catch (NumberFormatException e) {
                    log.warn("Invalid minute format in task file: {}", file);
                }
            }
            else if (line.startsWith("next_run=")) nextRun = line.substring(9);
            else if (line.startsWith("system=")) system = "yes".equals(line.substring(7));
        }
        prompt = body.toString().trim();
        if (id.isEmpty() || nextRun.isEmpty()) return null;
        try {
            return new Task(id, type, prompt, hour, minute, LocalDateTime.parse(nextRun), system);
        } catch (Exception e) {
            log.warn("Failed to parse task file: {}, error: {}", file, e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
    }

    /** 任务数据 */
    public record Task(
            String id,
            String type,       // "daily" 或 "once"
            String prompt,
            int hour,
            int minute,
            LocalDateTime nextRun,
            boolean system     // 系统任务不可由用户删除
    ) {
        public String nextRunStr() { return nextRun.toString(); }
        public String summary() {
            if ("once".equals(type))
                return "一次性 · " + nextRunStr().replace("T", " ") + " · " + prompt;
            return "每日 " + String.format("%02d:%02d", hour, minute) + " · " + prompt;
        }
    }
}
