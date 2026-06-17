package com.paicli.runtime.life;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 人生计划管理器。
 * <p>
 * 管理角色的五级人生计划：
 * - Life Plan（5-10年大局）
 * - Year Plan（本年度月度安排）
 * - Month Plan（本月周度安排）
 * - Week Plan（本周每日活动）
 * - Day Plan（今天具体做什么）
 * 
 * 存储位置：.paicli/souls/{角色名}/
 * - life-plan.md      - 人生大纲
 * - year-plan.md      - 年度计划
 * - month-plan.md     - 月度计划
 * - week-plan.md      - 周计划
 * - day-plan.md       - 今日计划
 */
public class LifePlanManager {
    private static final Logger log = LoggerFactory.getLogger(LifePlanManager.class);

    private final Path soulsDir;

    public LifePlanManager(Path projectPath) {
        this.soulsDir = projectPath.toAbsolutePath().normalize().resolve(".paicli/souls");
        try {
            Files.createDirectories(soulsDir);
        } catch (IOException e) {
            log.warn("Failed to create souls directory", e);
        }
    }

    /**
     * 获取当前角色目录
     */
    public Path getCurrentSoulDir() {
        try {
            if (!Files.isDirectory(soulsDir)) return null;
            try (var dirs = Files.newDirectoryStream(soulsDir, Files::isDirectory)) {
                for (Path dir : dirs) {
                    if (Files.isReadable(dir)) {
                        return dir;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to find current soul dir", e);
        }
        return null;
    }

    /**
     * 获取当前角色名
     */
    public String getCurrentCharacterName() {
        Path dir = getCurrentSoulDir();
        return dir != null ? dir.getFileName().toString() : "默认角色";
    }

    /**
     * 获取人生大纲（优先读取 timeline-life.md，兼容现有文件结构）
     */
    public String getLifePlan() {
        Path dir = getCurrentSoulDir();
        // 优先读取 timeline-life.md（现有文件结构）
        String content = readFile(dir, "timeline-life.md");
        if (!content.isEmpty()) {
            return content;
        }
        // 回退到 life-plan.md（新格式）
        return readFile(dir, "life-plan.md");
    }

    /**
     * 设置人生大纲
     */
    public void setLifePlan(String content) throws IOException {
        writeFile(getCurrentSoulDir(), "life-plan.md", content);
    }

    /**
     * 获取年度计划
     */
    public String getYearPlan() {
        return readFile(getCurrentSoulDir(), "year-plan.md");
    }

    /**
     * 设置年度计划
     */
    public void setYearPlan(String content) throws IOException {
        writeFile(getCurrentSoulDir(), "year-plan.md", content);
    }

    /**
     * 获取月度计划
     */
    public String getMonthPlan() {
        return readFile(getCurrentSoulDir(), "month-plan.md");
    }

    /**
     * 设置月度计划
     */
    public void setMonthPlan(String content) throws IOException {
        writeFile(getCurrentSoulDir(), "month-plan.md", content);
    }

    /**
     * 获取周计划
     */
    public String getWeekPlan() {
        return readFile(getCurrentSoulDir(), "week-plan.md");
    }

    /**
     * 设置周计划
     */
    public void setWeekPlan(String content) throws IOException {
        writeFile(getCurrentSoulDir(), "week-plan.md", content);
    }

    /**
     * 获取今日计划
     */
    public String getDayPlan() {
        return readFile(getCurrentSoulDir(), "day-plan.md");
    }

    /**
     * 设置今日计划
     */
    public void setDayPlan(String content) throws IOException {
        writeFile(getCurrentSoulDir(), "day-plan.md", content);
    }

    /**
     * 获取完整的人生上下文（用于注入到System Prompt）
     */
    public String getLifeContext() {
        StringBuilder sb = new StringBuilder();
        String lifePlan = getLifePlan();
        String yearPlan = getYearPlan();
        String monthPlan = getMonthPlan();
        String weekPlan = getWeekPlan();
        String dayPlan = getDayPlan();

        if (!lifePlan.isEmpty()) {
            sb.append("【人生规划】\n").append(lifePlan).append("\n\n");
        }
        if (!yearPlan.isEmpty()) {
            sb.append("【今年计划】\n").append(yearPlan).append("\n\n");
        }
        if (!monthPlan.isEmpty()) {
            sb.append("【本月计划】\n").append(monthPlan).append("\n\n");
        }
        if (!weekPlan.isEmpty()) {
            sb.append("【本周计划】\n").append(weekPlan).append("\n\n");
        }
        if (!dayPlan.isEmpty()) {
            sb.append("【今日安排】\n").append(dayPlan).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取今日日志
     */
    public String getTodayLog() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return readFile(getCurrentSoulDir(), "logs/" + today + ".md");
    }

    /**
     * 写入今日日志
     */
    public void writeTodayLog(String content) throws IOException {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path logDir = getCurrentSoulDir().resolve("logs");
        Files.createDirectories(logDir);
        writeFile(logDir, today + ".md", content);
    }

    /**
     * 获取人物信息摘要
     */
    public String getPersonaSummary() {
        String soulMd = readFile(getCurrentSoulDir(), "soul.md");
        if (soulMd.isEmpty()) {
            return "未设定角色";
        }
        // 简单提取前几行作为摘要
        String[] lines = soulMd.split("\n");
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.startsWith("#")) {
                summary.append(line).append("\n");
                count++;
                if (count >= 5) break;
            }
        }
        return summary.toString().trim();
    }

    /**
     * 检查是否需要更新计划
     */
    public boolean needsYearPlanUpdate() {
        String yearPlan = getYearPlan();
        if (yearPlan.isEmpty()) return true;
        // 检查是否包含当前年份
        String currentYear = String.valueOf(LocalDateTime.now().getYear());
        return !yearPlan.contains(currentYear);
    }

    /**
     * 检查是否需要更新月度计划
     */
    public boolean needsMonthPlanUpdate() {
        String monthPlan = getMonthPlan();
        if (monthPlan.isEmpty()) return true;
        // 检查是否包含当前月份
        YearMonth current = YearMonth.now();
        String currentMonth = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return !monthPlan.contains(currentMonth);
    }

    // ========== Private Helpers ==========

    private String readFile(Path dir, String filename) {
        if (dir == null || filename == null) return "";
        Path file = dir.resolve(filename);
        if (!Files.isReadable(file)) return "";
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            log.warn("Failed to read file: {}", file);
            return "";
        }
    }

    private void writeFile(Path dir, String filename, String content) throws IOException {
        if (dir == null || filename == null) {
            throw new IOException("Invalid path or filename");
        }
        Path file = dir.resolve(filename);
        Files.writeString(file, content == null ? "" : content);
        log.info("Written file: {}", file);
    }

    /**
     * 生成新的人生（重置所有计划）
     */
    public void resetLifePlan() throws IOException {
        Path dir = getCurrentSoulDir();
        if (dir == null) {
            throw new IOException("未找到角色目录");
        }
        // 保留soul.md和timeline-life.md，删除其他计划文件
        String[] planFiles = {"life-plan.md", "year-plan.md", "month-plan.md", "week-plan.md", "day-plan.md"};
        for (String file : planFiles) {
            Path planFile = dir.resolve(file);
            if (Files.exists(planFile)) {
                Files.delete(planFile);
            }
        }
        log.info("Life plan reset for character");
    }

    /**
     * 获取所有角色的计划目录
     */
    public List<Path> listCharacterDirs() {
        List<Path> dirs = new ArrayList<>();
        if (!Files.isDirectory(soulsDir)) return dirs;
        try (var stream = Files.newDirectoryStream(soulsDir, Files::isDirectory)) {
            for (Path dir : stream) {
                dirs.add(dir);
            }
        } catch (IOException e) {
            log.warn("Failed to list character dirs", e);
        }
        return dirs;
    }
}
