# 内心独白功能设计

## 概述

为角色添加内心独白功能，让角色在回复前可以表达内心想法（如 `(OS: 宝宝好可爱)`），增强角色扮演的沉浸感。

## 需求

- 角色可以在回复前显示内心独白
- 用户可配置开关、频率、触发情境
- 通过命令行命令控制设置
- 内心独白用 `(OS: ...)` 格式显示

## 设计

### 1. 配置存储

在角色的 `soul.md` 文件中添加内心独白配置：

```yaml
## 内心独白设置
inner_monologue:
  enabled: true          # 是否启用
  frequency: medium      # 频率: high/medium/low
  triggers:              # 触发情境（用于medium频率）
    - 被夸奖时
    - 心动时
    - 害羞时
```

### 2. Prompt指令

在 `handoff-chat.md` 中添加内心独白生成规则：

```markdown
## 内心独白规则

如果角色启用了内心独白，在回复前可以用 <os>...</os> 标签表达内心想法：
- 内容是角色真实的内心感受，不是给用户看的
- 简短、自然，1-2句话
- 根据频率配置决定是否生成：
  - high: 每次回复都生成
  - medium: 根据触发情境生成
  - low: 偶尔生成

示例输出：
<os>宝宝今天好可爱</os>
嗯，今天怎么样？
```

### 3. 输出解析

在 `Agent.java` 中添加解析逻辑：

```java
// 解析 <os>...</os> 标签
Pattern OS_PATTERN = Pattern.compile("<os>(.*?)</os>", Pattern.DOTALL);

String parseInnerMonologue(String content) {
    Matcher matcher = OS_PATTERN.matcher(content);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
        String os = matcher.group(1).trim();
        result.append("(OS: ").append(os).append(")\n");
    }
    // 移除标签后的正文
    String remaining = matcher.replaceAll("").trim();
    if (!remaining.isEmpty()) {
        result.append(remaining);
    }
    return result.toString();
}
```

### 4. 命令实现

添加以下命令：

| 命令 | 作用 |
|------|------|
| `/内心独白` | 查看当前设置 |
| `/内心独白 开启` | 开启内心独白 |
| `/内心独白 关闭` | 关闭内心独白 |
| `/内心独白 频率 高/中/低` | 设置频率 |
| `/内心独白 触发 添加 <情境>` | 添加触发情境 |
| `/内心独白 触发 删除 <情境>` | 删除触发情境 |
| `/内心独白 触发 清空` | 清空触发情境 |

### 5. 显示效果

```
(OS: 宝宝今天好可爱)
嗯，今天怎么样？
```

内心独白用灰色样式显示，与正文区分。

## 实现步骤

1. 在 `soul.md` 解析逻辑中添加 `inner_monologue` 配置读取
2. 修改 `handoff-chat.md`，添加内心独白生成规则
3. 在 `Agent.java` 中添加 `<os>...</os>` 标签解析
4. 在 `CliCommandParser.java` 中添加命令解析
5. 在 `Main.java` 中添加命令处理逻辑
6. 添加配置持久化（修改 `soul.md` 文件）

## 文件修改清单

| 文件 | 修改内容 |
|------|---------|
| `soul.md` | 添加 inner_monologue 配置 |
| `SoulFile.java` | 添加配置读取 |
| `handoff-chat.md` | 添加内心独白生成规则 |
| `Agent.java` | 添加标签解析 |
| `CliCommandParser.java` | 添加命令解析 |
| `Main.java` | 添加命令处理 |
| `InnerMonologueConfig.java` | 新建配置类 |
