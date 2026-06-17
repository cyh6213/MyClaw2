package com.paicli.soul;

import java.util.ArrayList;
import java.util.List;

/**
 * 内心独白配置
 *
 * 控制角色是否在回复前显示内心独白（OS: ...）
 */
public class InnerMonologueConfig {
    private boolean enabled = false;
    private Frequency frequency = Frequency.MEDIUM;
    private List<String> triggers = new ArrayList<>();

    public enum Frequency {
        HIGH("高", "每次回复都生成"),
        MEDIUM("中", "根据触发情境生成"),
        LOW("低", "偶尔生成");

        private final String label;
        private final String description;

        Frequency(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public static Frequency fromLabel(String label) {
            for (Frequency f : values()) {
                if (f.label.equals(label)) {
                    return f;
                }
            }
            return MEDIUM;
        }
    }

    public InnerMonologueConfig() {
        // 默认触发情境
        triggers.add("被夸奖时");
        triggers.add("心动时");
        triggers.add("害羞时");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = triggers;
    }

    public void addTrigger(String trigger) {
        if (!triggers.contains(trigger)) {
            triggers.add(trigger);
        }
    }

    public void removeTrigger(String trigger) {
        triggers.remove(trigger);
    }

    public void clearTriggers() {
        triggers.clear();
    }

    /**
     * 从 soul.md 内容中解析内心独白配置
     */
    public static InnerMonologueConfig parse(String soulContent) {
        InnerMonologueConfig config = new InnerMonologueConfig();

        if (soulContent == null || soulContent.isBlank()) {
            return config;
        }

        // 解析 inner_monologue 配置块
        String[] lines = soulContent.split("\n");
        boolean inConfig = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("## 内心独白设置") || trimmed.startsWith("##inner_monologue")) {
                inConfig = true;
                continue;
            }

            if (inConfig && trimmed.startsWith("## ")) {
                // 进入下一个 section，结束解析
                break;
            }

            if (inConfig) {
                if (trimmed.startsWith("enabled:")) {
                    String value = trimmed.substring("enabled:".length()).trim();
                    config.setEnabled("true".equalsIgnoreCase(value));
                } else if (trimmed.startsWith("frequency:")) {
                    String value = trimmed.substring("frequency:".length()).trim();
                    config.setFrequency(Frequency.fromLabel(value));
                } else if (trimmed.startsWith("- ") && !trimmed.startsWith("- **")) {
                    // 触发情境列表项
                    String trigger = trimmed.substring(2).trim();
                    if (!trigger.isEmpty()) {
                        config.addTrigger(trigger);
                    }
                }
            }
        }

        return config;
    }

    /**
     * 生成配置的 Markdown 格式
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 内心独白设置\n");
        sb.append("inner_monologue:\n");
        sb.append("  enabled: ").append(enabled).append("\n");
        sb.append("  frequency: ").append(frequency.getLabel()).append("\n");
        sb.append("  triggers:\n");
        for (String trigger : triggers) {
            sb.append("    - ").append(trigger).append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成用于 prompt 的内心独白规则说明
     */
    public String toPromptRule() {
        if (!enabled) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 内心独白规则\n\n");
        sb.append("你可以在回复前用 <os>...</os> 标签表达内心想法：\n");
        sb.append("- 内容是角色真实的内心感受，不是给用户看的\n");
        sb.append("- 简短、自然，1-2句话\n");

        switch (frequency) {
            case HIGH:
                sb.append("- 每次回复都生成内心独白\n");
                break;
            case MEDIUM:
                sb.append("- 根据以下情境生成内心独白：");
                sb.append(String.join("、", triggers));
                sb.append("\n");
                break;
            case LOW:
                sb.append("- 偶尔生成内心独白\n");
                break;
        }

        sb.append("\n示例输出：\n");
        sb.append("<os>宝宝今天好可爱</os>\n");
        sb.append("嗯，今天怎么样？\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return "InnerMonologueConfig{" +
                "enabled=" + enabled +
                ", frequency=" + frequency +
                ", triggers=" + triggers +
                '}';
    }
}
