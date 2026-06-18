# AGENTS.md

仓库给 Agent / 新线程使用的首读入口。

## 信息优先级

1. 代码实际行为 > 2. `AGENTS.md` > 3. `PAI.md` > 4. `README.md`

## 项目快照

- 项目名：**MyClaw**
- 定位：AI 角色扮演伴侣，基于 PaiCLI 改造而来
- 核心能力：角色扮演对话、内心独白、主动问候、长期记忆、微信通道、定时任务、人生计划
- `PAI.md` 是项目级记忆文件：启动时自动注入 system prompt，适合团队共享的长期稳定规则
- 当前产物：`target/myclaw-1.0-SNAPSHOT.jar`

## 运行前提

- Java 17+ / Maven
- 至少一个 API Key：`GLM_API_KEY` / `DEEPSEEK_API_KEY` / `STEP_API_KEY` / `KIMI_API_KEY` / `SILICONFLOW_API_KEY` 等

## 常用命令

```bash
cp .env.example .env
mvn clean package -DskipTests
java -jar target/myclaw-1.0-SNAPSHOT.jar
java -jar target/myclaw-1.0-SNAPSHOT.jar wechat setup   # 绑定微信通道
java -jar target/myclaw-1.0-SNAPSHOT.jar wechat start   # 前台启动微信通道
docker compose up -d                                      # Docker 部署
```

## 架构概览

```
MyClaw
├── Agent (ReAct 循环)          # 核心对话引擎
├── MemoryManager               # 记忆管理（短期 + 长期）
├── HindsightMemory             # 向量数据库长期记忆
├── PromptAssembler             # Prompt 分层组装
├── SkillRegistry               # Skill 系统
├── ProactiveScheduler          # 主动对话调度器
├── ScheduledTaskManager        # 定时任务管理器
├── LifePlanManager             # 人生计划管理器
├── InnerMonologueConfig        # 内心独白配置
├── Wechat Channel              # 微信 iLink 通道
└── Runtime API                 # HTTP API 服务
```

## 仓库结构

```
src/main/java/com/paicli/
├── agent/       Agent.java（核心 ReAct 循环）
├── cli/         Main.java, CliCommandParser.java
├── llm/         GLMClient, DeepSeekClient, StepClient, KimiClient 等
├── memory/      MemoryManager, LongTermMemory, HindsightMemory
├── prompt/      PromptAssembler, PromptRepository
├── soul/        InnerMonologueConfig
├── runtime/
│   ├── proactive/  ProactiveScheduler（主动对话）
│   ├── life/       LifePlanManager（人生计划）
│   └── task/       ScheduledTaskManager, DurableTaskManager
├── wechat/      iLink client, message loop
├── render/      Renderer, InlineRenderer, PlainRenderer
├── tool/        ToolRegistry
├── mcp/         MCP 协议支持
├── skill/       Skill 系统
├── snapshot/    Git 快照与回滚
├── policy/      PathGuard, CommandGuard, AuditLog
├── hitl/        HITL 审批流
├── web/         联网搜索
├── rag/         RAG 代码检索
├── image/       图片输入
├── lsp/         LSP 诊断
├── browser/     浏览器控制
├── config/      配置管理
└── context/     上下文管理
```

## 核心模块说明

### 角色系统

角色数据存储在 `.paicli/souls/<角色名>/` 目录下：
- `soul.md` — 角色核心配置（性格、喜好、说话风格、禁止行为、内心独白设置）
- `timeline-life.md` — 角色人生动线
- `life-plan.md` / `year-plan.md` / `month-plan.md` / `week-plan.md` / `day-plan.md` — 五级人生计划

CLI 命令：`/创建角色`、`/切换角色`、`/角色列表`、`/人格`、`/人生计划`

### 内心独白

角色可以在回复中加入内心独白，用 `<os>...</os>` 标签包裹，显示为 `(OS: ...)` 格式。
由 `InnerMonologueConfig` 控制开关、频率和触发情境。

CLI 命令：`/内心独白`、`/内心独白 开启|关闭`、`/内心独白 频率 高|中|低`

### 主动对话

`ProactiveScheduler` 通过心跳扫描检测触发时机，主动向用户发起对话。

触发规则：
- 禁止时段：凌晨 0:00-7:00 不触发
- 空闲触发：上次对话结束超过 30 分钟，每天最多 2 次
- 时段触发：早 8-9、午 12-13、晚 20-22 各 1 次，每天最多 3 次
- 连续 2 次被忽略 → 当天频率减半

CLI 命令：`/主动状态`、`/静音 <小时>`、`/常态`

### 定时任务

`ScheduledTaskManager` 管理定时任务，系统默认每天凌晨 3:00 运行日常模拟任务。

CLI 命令：`/定时任务`、`/任务运行 <id>`

### 记忆管理

- **短期记忆** — 当前对话上下文
- **长期记忆** — 通过 Hindsight 向量数据库存储（不可用时回退本地文件）
- 对话历史持久化到 `.paicli/conversations/last-session.json`，最近 50 轮

CLI 命令：`/memory`、`/memory list`、`/memory search`、`/save`

### 微信通道

通过 iLink 协议实现微信消息收发，支持扫码绑定、消息收发、控制命令。

CLI 命令：`/wechat`、`/wechat setup`、`/wechat status`、`/wechat stop`

### 人生计划

`LifePlanManager` 管理角色的五级人生计划：
- Life Plan（5-10年大局）
- Year Plan（本年度月度安排）
- Month Plan（本月周度安排）
- Week Plan（本周每日活动）
- Day Plan（今天具体做什么）

## 关键行为约束

### Memory

- 长期记忆只通过 `/save` 或用户明确要求保存；不要自动提取事实
- 长期记忆只保存跨会话稳定事实，不保存临时指令
- 默认项目级作用域，跨项目通用偏好才用 global

### 内心独白

- 内心独白用 `<os>...</os>` 标签包裹
- 显示格式为 `(OS: ...)`
- 频率由配置控制，不要每轮都生成

### 主动对话

- 主动对话只在允许时段触发
- 触发后需要冷却若干轮
- 用户静音期间不触发

### 微信通道

- 没有人工审批面板，走非交互式默认拒绝策略
- 只读工具默认允许
- `execute_command` 必须精确命中命令白名单
- `write_file` 受 PathGuard 限制

## 修改时的硬规则

### 1. 改行为 → 同步文档

`AGENTS.md` / `README.md`

### 2. 改命令入口 → 联动

`Main.java` + `CliCommandParser.java` + 测试 + `README.md` + `AGENTS.md`

### 3. 改工具集 → 联动

`ToolRegistry.java` + Agent 提示词 + 文档

### 4. 改模型/接口 → 联动

对应 Client + `LlmClientFactory.java` + `.env.example` + 文档

### 5. 不提交 `.env` / 真实 API Key / `target/` 产物

## 给新线程的导航

1. 先看本文件 → 2. `README.md` → 3. `Main.java` → 4. 按任务进入对应模块

| 任务类型 | 先看 |
|----------|------|
| CLI 命令 | Main.java + CliCommandParser.java |
| 角色系统 | Main.java（创建角色/切换角色命令处理） |
| 内心独白 | InnerMonologueConfig.java + Agent.java |
| 主动对话 | ProactiveScheduler.java |
| 定时任务 | ScheduledTaskManager.java |
| 人生计划 | LifePlanManager.java |
| 记忆管理 | MemoryManager.java + HindsightMemory.java |
| 微信通道 | wechat/ 包 |
| 对话引擎 | Agent.java |
| Prompt | PromptAssembler.java + PromptRepository.java |
