# PAI.md

## Commands

- 构建：`mvn clean package -DskipTests` 默认跳过测试，优先产出可手工验收 jar。
- 运行：`java -jar target/myclaw-1.0-SNAPSHOT.jar`
- Docker 部署：`docker compose up -d`

## What This Is

MyClaw 是 AI 角色扮演伴侣，基于 PaiCLI 改造而来。不再是编程助手，而是一个有血有肉的 AI 伙伴——可以设定角色、聊天陪伴、主动问候、记住你们之间的事。

## Architecture

- 核心对话引擎走 ReAct 循环，共享 `ToolRegistry` / `MemoryManager` / `SnapshotService`。
- 角色数据存储在 `.paicli/souls/<角色名>/` 目录下。
- system prompt 由 `PromptAssembler` 分层组装，内置 prompt 在 `src/main/resources/prompts/`，支持 `~/.paicli/prompts/` 和 `.paicli/prompts/` 覆盖。
- 主动对话由 `ProactiveScheduler` 驱动，定时任务由 `ScheduledTaskManager` 驱动。
- 长期记忆走 Hindsight 向量数据库，不可用时自动回退本地文件存储。

## Things That Will Bite You

- 改行为要同步 `AGENTS.md` / `README.md`。
- 改命令入口要联动 `Main.java`、`CliCommandParser.java`、测试、`README.md`、`AGENTS.md`。
- 长期记忆只通过 `/save` 或用户明确要求保存；不要自动提取临时事实。
- `ctx` 表示下一轮仍会带入请求的上下文估算；`in/out/cache` 表示最近任务 LLM 调用统计，不要混用。

## Don't

- 不提交 `.env`、真实 API Key、`target/` 产物。
- 不在交互主路径新增裸 `System.out.println`；优先走 `Renderer.stream()`。
