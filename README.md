# MyClaw

一个 AI 角色扮演伴侣，基于 PaiCLI 改造而来。

不再是编程助手，而是一个有血有肉的 AI 伙伴——可以设定角色、聊天陪伴、主动问候、记住你们之间的事。支持 CLI 终端和微信双通道交互。

## 快速开始

### 本地运行

```bash
cd paicli-main

# 配置 API Key
cp .env.example .env
# 编辑 .env，填入你的 API Key（支持 GLM / DeepSeek / Step / Kimi / SiliconFlow 等）

# 构建
mvn clean package -DskipTests

# 运行
java -jar target/myclaw-1.0-SNAPSHOT.jar
```

### Docker 部署

```bash
# 构建镜像
docker compose build

# 启动（含 Hindsight 长期记忆服务）
docker compose up -d

# 查看日志
docker compose logs -f paicli
```

首次运行进入交互式 CLI，输入 `/创建角色` 开始。

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
- `life-plan.md` / `year-plan.md` / `month-plan.md` / `week-plan.md` / `day-plan.md` — 五级人生计划

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

角色可以在特定时间主动给你发消息。

| 命令 | 说明 |
|------|------|
| `/主动状态` | 查看主动对话状态 |
| `/静音 <小时>` | 静音指定小时 |
| `/常态` | 恢复常态 |

触发规则：
- 禁止时段：凌晨 0:00-7:00 不触发
- 空闲触发：上次对话结束超过 30 分钟，每天最多 2 次
- 时段触发：早 8-9、午 12-13、晚 20-22 各 1 次，每天最多 3 次
- 连续 2 次被忽略 → 当天频率减半

### 定时任务

系统每天凌晨 3:00 自动运行日常模拟任务，推进角色生活。

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

在服务器上部署后，可以通过微信与角色聊天。

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
| `/export` | 导出对话为 Markdown |

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
├── Agent               # 核心对话引擎（ReAct 循环）
├── MemoryManager       # 记忆管理（短期 + 长期）
├── Hindsight           # 向量数据库长期记忆
├── PromptAssembler     # Prompt 组装器
├── SkillRegistry       # Skill 系统
├── ProactiveScheduler  # 主动对话调度器
├── ScheduledTaskManager # 定时任务管理器
├── LifePlanManager     # 人生计划管理器
├── InnerMonologueConfig # 内心独白配置
├── Wechat Channel      # 微信 iLink 通道
└── Runtime API         # HTTP API 服务
```

### 依赖

- Java 17+
- Docker（可选，用于 Hindsight 长期记忆和容器化部署）
- 一个兼容 OpenAI 格式的 LLM API Key

## Hindsight 长期记忆

Hindsight 是独立的向量数据库服务，用于存储和检索长期记忆。启动方式：

```bash
docker run -d --pull never --name hindsight \
  -p 8888:8888 -p 9999:9999 \
  -e HINDSIGHT_API_LLM_API_KEY=sk-xxx \
  -e HINDSIGHT_API_LLM_BASE_URL=https://api.siliconflow.cn/v1 \
  -e HINDSIGHT_API_LLM_MODEL=Qwen/Qwen2.5-7B-Instruct \
  -e HF_ENDPOINT=https://hf-mirror.com \
  ghcr.io/vectorize-io/hindsight:latest
```

如果 Hindsight 不可用，会自动回退到本地文件存储，功能不受影响。

## 部署

### Docker Compose（推荐）

```bash
# 1. 克隆代码到服务器
git clone https://github.com/cyh6213/MyClaw2.git /opt/paicli

# 2. 配置环境变量
cd /opt/paicli/paicli-main
cp .env.example .env
# 编辑 .env，填入 API Key

# 3. 启动
docker compose up -d

# 4. 进入交互模式
docker attach paicli
```

### GitHub Actions 自动部署

推送代码到 `main` 分支后，GitHub Actions 会自动：
1. 编译打包
2. 构建 Docker 镜像
3. 推送到容器仓库
4. SSH 到服务器拉取新镜像并重启

需要在 GitHub 仓库设置 Secrets：
| Secret | 说明 |
|--------|------|
| `SERVER_HOST` | 云服务器 IP |
| `SERVER_USER` | SSH 用户名 |
| `SERVER_SSH_KEY` | SSH 私钥 |

### 崩溃自恢复

Docker Compose 已配置 `restart: always`，进程崩溃后自动拉起，持久化数据（记忆、配置、任务队列）不会丢失。

## 构建

```bash
cd paicli-main
mvn clean package -DskipTests
java -jar target/myclaw-1.0-SNAPSHOT.jar
```
