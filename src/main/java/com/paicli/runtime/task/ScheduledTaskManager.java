package com.paicli.runtime.task;

import com.paicli.agent.Agent;
import com.paicli.config.PaiCliConfig;
import com.paicli.llm.LlmClient;
import com.paicli.llm.LlmClientFactory;
import com.paicli.prompt.PromptMode;
import com.paicli.tool.ToolRegistry;
import com.paicli.render.PlainRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务管理器。
 * <p>
 * 支持运行时添加/删除/查看定时任务，任务到点后创建独立 Agent（MANAGE 模式）执行 prompt。
 * 支持两种类型：
 * - daily：每日循环任务，如"每天早上8点"（已存在）
 * - once：一次性任务，到点执行后自动删除
 * 数据持久化到 SQLite: .paicli/scheduler/tasks.db
 */
public class ScheduledTaskManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskManager.class);

    private final Path projectPath;
    private final Connection connection;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;

    public ScheduledTaskManager(Path projectPath) throws SQLException, IOException {
        this.projectPath = projectPath.toAbsolutePath().normalize();
        Path dbDir = this.projectPath.resolve(".paicli/scheduler");
        Files.createDirectories(dbDir);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbDir.resolve("tasks.db"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paicli-scheduler");
            t.setDaemon(true);
            return t;
        });
        initTables();
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::tick, 0, 15, TimeUnit.SECONDS);
        log.info("ScheduledTaskManager started, db={}", dbPath());
    }

    // ========== Public CRUD API ==========

    /** 添加一个每日循环任务，time 格式 "HH:mm" */
    public ScheduledTask addDailyTask(String id, String prompt, String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        LocalTime taskTime = LocalTime.of(hour, minute);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = LocalDateTime.of(now.toLocalDate(), taskTime);
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }

        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO scheduled_tasks (id, prompt, task_type, hour, minute, next_run, enabled)
                VALUES (?, ?, 'daily', ?, ?, ?, 1)
                """)) {
            ps.setString(1, id);
            ps.setString(2, prompt);
            ps.setInt(3, hour);
            ps.setInt(4, minute);
            ps.setString(5, nextRun.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("添加每日任务失败: " + e.getMessage(), e);
        }
        log.info("Daily task added: id={}, time={}, next_run={}", id, time, nextRun);
        return new ScheduledTask(id, prompt, "daily", hour, minute, nextRun.toString(), true);
    }

    /** 添加一次性任务，scheduledAt 为绝对时间 LocalDateTime */
    public ScheduledTask addOneTimeTask(String id, String prompt, LocalDateTime scheduledAt) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO scheduled_tasks (id, prompt, task_type, hour, minute, next_run, enabled)
                VALUES (?, ?, 'once', 0, 0, ?, 1)
                """)) {
            ps.setString(1, id);
            ps.setString(2, prompt);
            ps.setString(3, scheduledAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("添加一次性任务失败: " + e.getMessage(), e);
        }
        log.info("One-time task added: id={}, scheduled_at={}", id, scheduledAt);
        return new ScheduledTask(id, prompt, "once", 0, 0, scheduledAt.toString(), true);
    }

    /** 删除定时任务 */
    public boolean removeTask(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM scheduled_tasks WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("删除定时任务失败: " + e.getMessage(), e);
        }
    }

    /** 列出所有启用的定时任务 */
    public List<ScheduledTask> listTasks() {
        List<ScheduledTask> tasks = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM scheduled_tasks WHERE enabled = 1 ORDER BY next_run")) {
            while (rs.next()) {
                tasks.add(fromRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("列出定时任务失败: " + e.getMessage(), e);
        }
        return tasks;
    }

    public Path dbPath() {
        return projectPath.resolve(".paicli/scheduler/tasks.db");
    }

    // ========== Internal ==========

    private void tick() {
        if (!running) return;
        try {
            List<ScheduledTask> dueTasks = findDueTasks();
            for (ScheduledTask task : dueTasks) {
                log.info("Scheduled task triggered: id={}, type={}", task.id, task.taskType);
                executeTask(task);
                if ("once".equals(task.taskType)) {
                    // 一次性任务：执行完就删除
                    removeTask(task.id);
                    log.info("One-time task done and removed: id={}", task.id);
                } else {
                    // 每日任务：推到明天
                    LocalDateTime nextRun = LocalDateTime.now()
                            .withHour(task.hour).withMinute(task.minute).withSecond(0);
                    if (nextRun.isBefore(LocalDateTime.now()) || nextRun.isEqual(LocalDateTime.now())) {
                        nextRun = nextRun.plusDays(1);
                    }
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE scheduled_tasks SET next_run = ? WHERE id = ?")) {
                        ps.setString(1, nextRun.toString());
                        ps.setString(2, task.id);
                        ps.executeUpdate();
                    }
                    log.info("Daily task rescheduled: id={}, next_run={}", task.id, nextRun);
                }
            }
        } catch (Exception e) {
            log.warn("Scheduler tick error", e);
        }
    }

    private List<ScheduledTask> findDueTasks() throws SQLException {
        List<ScheduledTask> due = new ArrayList<>();
        String now = LocalDateTime.now().toString();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM scheduled_tasks WHERE enabled = 1 AND next_run <= ?")) {
            ps.setString(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(fromRow(rs));
                }
            }
        }
        return due;
    }

    private void executeTask(ScheduledTask task) {
        try {
            PaiCliConfig config = PaiCliConfig.load();
            LlmClient client = LlmClientFactory.createFromConfig(config);
            if (client == null) {
                log.warn("Scheduled task skipped (no LLM client): id={}", task.id);
                return;
            }
            ToolRegistry registry = new ToolRegistry();
            registry.setProjectPath(projectPath.toString());
            Agent agent = new Agent(client, registry);
            agent.setRenderer(new PlainRenderer());
            agent.setMode(PromptMode.MANAGE);
            agent.setReturnFinalResponseWhenStreamed(true);
            String result = agent.run(task.prompt);
            log.info("Scheduled task result: id={}, length={}", task.id, result == null ? 0 : result.length());
        } catch (Exception e) {
            log.warn("Scheduled task execution failed: id=" + task.id, e);
        }
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS scheduled_tasks (
                        id TEXT PRIMARY KEY,
                        prompt TEXT NOT NULL,
                        task_type TEXT NOT NULL DEFAULT 'daily',
                        hour INTEGER NOT NULL DEFAULT 0,
                        minute INTEGER NOT NULL DEFAULT 0,
                        next_run TEXT NOT NULL,
                        enabled INTEGER DEFAULT 1
                    )
                    """);
        }
    }

    private ScheduledTask fromRow(ResultSet rs) throws SQLException {
        return new ScheduledTask(
                rs.getString("id"),
                rs.getString("prompt"),
                rs.getString("task_type"),
                rs.getInt("hour"),
                rs.getInt("minute"),
                rs.getString("next_run"),
                rs.getInt("enabled") == 1
        );
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    /** 定时任务数据记录 */
    public record ScheduledTask(
            String id,
            String prompt,
            String taskType,
            int hour,
            int minute,
            String nextRun,
            boolean enabled
    ) {
        public String timeDisplay() {
            if ("once".equals(taskType)) {
                return "一次性·" + nextRun.substring(11, 16); // 显示 HH:mm
            }
            return String.format("%02d:%02d", hour, minute);
        }

        public String summary() {
            if ("once".equals(taskType)) {
                return "一次性 · " + nextRun.replace("T", " ") + " · " + prompt;
            }
            return "每日 " + timeDisplay() + " · " + prompt;
        }
    }
}
