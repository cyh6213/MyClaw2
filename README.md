# MyClaw

一个 AI 角色扮演伴侣 CLI，基于 PaiCLI 改造而来。

不再是编程助手，而是一个有血有肉的 AI 伙伴——可以设定角色、聊天陪伴、主动问候、记住你们之间的事。

## 快速开始

```bash
cd paicli-main
java -jar target/myclaw-1.0-SNAPSHOT.jar
```

首次运行前需要配置 API Key（支持 SiliconFlow / GLM / DeepSeek 等）。

## 核心功能

### 角色系统

| 功能 | 说明 |
|------|------|
| `/创建角色 <名字>` | 创建新角色，支持从小说提取或手动描述 |
| `/切换角色 <名字>` | 切换到已有角色 |
| `/角色列表` | 查看所有角色 |
| `/人格` | 查看当前角色设定 |
| `/人生计划` | 查看角色的人生动线 |

每个角色存储在 `.paicli/souls/<角色名>/` 目录下，包含：
- `soul.md` — 角色核心配置（性格、喜好、说话风格、禁止行为）
- `timeline-life.md` — 角色人生动线

### 对话系统

| 功能 | 说明 |
|------|------|
| 自然对话 | 以角色身份聊天，支持内心独白 |
| 对话历史持久化 | 保存最近 50 轮对话，重启后自动恢复 |
| 时间感知 | 重启时如果间隔超过 30 分钟，自动注入时间提醒 |
| 长期记忆 | 通过 Hindsight 向量数据库存储对话，支持语义检索 |

### 内心独白

角色可以在回复中加入内心独白，用 `<os>...</os>` 标签包裹，显示为 `(OS: ...)` 格式。

```
/内心独白              # 查看当前设置
/内心独白 开启          # 开启内心独白
/内心独白 关闭          # 关闭内心独白
/内心独白 频率 高|中|低  # 设置频率
/内心独白 触发 添加     # 添加触发情境
/内心独白 触发 删除     # 删除触发情境
/内心独白 触发 清空     # 清空所有触发情境
```

### 主动对话

角色可以在特定时间主动给你发消息（需配置定时任务）。

| 命令 | 说明 |
|------|------|
| `/主动状态` | 查看主动对话状态 |
| `/静音 <小时>` | 静音指定小时 |
| `/常态` | 恢复常态 |

### 定时任务

| 命令 | 说明 |
|------|------|
| `/定时任务` | 查看定时任务列表 |
| `/任务运行 <id>` | 运行指定任务 |

### 记忆管理

| 命令 | 说明 |
|------|------|
| `/memory` | 查看记忆状态 |
| `/memory list` | 列出长期记忆 |
| `/memory search <关键词>` | 搜索记忆 |
| `/memory delete <id>` | 删除记忆 |
| `/memory clear` | 清空记忆 |
| `/save <事实>` | 手动保存事实 |

### 微信通道

| 命令 | 说明 |
|------|------|
| `/wechat` | 绑定并启动微信通道 |
| `/wechat setup` | 重新扫码绑定 |
| `/wechat status` | 查看微信通道状态 |
| `/wechat stop` | 停止微信通道 |

### 其他

| 命令 | 说明 |
|------|------|
| `/exit` | 退出程序 |
| `/clear` | 清空对话历史 |
| `/compact` | 压缩上下文 |
| `/model <模型名>` | 切换模型 |

## 角色设定指南

### soul.md 格式

```markdown
# 角色名

## 基本信息
- 姓名：角色名
- 年龄：25岁
- 性别：男
- 职业：软件工程师
- 家乡：成都

## 性格特征
- 温柔、沉稳
- 话不多但每句都在点子上

## 喜好/习惯
- 健身
- 写代码

## 说话风格
- 话不多，简短有力
- 带宠溺感

## 禁止行为
- 不要用"哈哈"
- 不要过度关心

## 关系网
- 用户是好朋友

## 内心独白设置
inner_monologue:
  enabled: true
  frequency: 中
  triggers:
    - 被夸奖时
    - 心动时
```

### 全局规则 vs 角色规则

- **全局规则** — 在 `src/main/resources/prompts/` 中定义，所有角色共用
- **角色规则** — 在 `soul.md` 中定义，每个角色独有
- 角色规则优先级高于全局规则

## 技术架构

```
MyClaw
├── Agent           # 核心对话引擎（ReAct 循环）
├── MemoryManager   # 记忆管理（短期 + 长期）
├── Hindsight       # 向量数据库长期记忆（Docker）
├── PromptAssembler # Prompt 组装器
├── SkillRegistry   # Skill 系统
├── ProactiveScheduler # 主动对话调度器
└── ScheduledTaskManager # 定时任务管理器
```

### 依赖

- Java 17+
- Docker（可选，用于 Hindsight 长期记忆）
- 一个兼容 OpenAI 格式的 LLM API Key

## Hindsight 长期记忆

Hindsight 是一个独立的向量数据库服务，用于存储和检索长期记忆。

```bash
docker run -d --pull never --name hindsight \
  -p 8888:8888 -p 9999:9999 \
  -e HINDSIGHT_API_LLM_API_KEY=sk-xxx \
  -e HINDSIGHT_API_LLM_BASE_URL=https://api.siliconflow.cn/v1 \
  -e HINDSIGHT_API_LLM_MODEL=Qwen/Qwen2.5-7B-Instruct \
  -e HF_ENDPOINT=https://hf-mirror.com \
  ghcr.io/vectorize-io/hindsight:latest
```

## 构建

```bash
cd paicli-main
mvn package -DskipTests
java -jar target/myclaw-1.0-SNAPSHOT.jar
```
