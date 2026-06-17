package com.paicli.cli;

final class CliCommandParser {

    enum CommandType {
        // ========== 基础控制 ==========
        NONE,                   // 无命令
        UNKNOWN_COMMAND,        // 未知命令
        EXIT,                   // 退出程序
        CANCEL,                 // 取消当前任务
        CLEAR,                  // 清空对话历史
        COMPACT,                // 压缩历史上下文
        HISTORY_CLEAR,          // 清空输入历史

        // ========== 模型/模式 ==========
        SWITCH_MODEL,           // 切换模型
        SWITCH_PLAN,            // 切换计划模式
        SWITCH_TEAM,            // 切换团队模式
        SWITCH_MANAGE,          // 切换管理模式
        SWITCH_HITL,            // 人机交互模式开关

        // ========== 记忆管理 ==========
        MEMORY_STATUS,          // 记忆状态
        MEMORY_CLEAR,           // 清空记忆
        MEMORY_LIST,            // 列出记忆
        MEMORY_DELETE,          // 删除记忆
        MEMORY_SEARCH,          // 搜索记忆
        MEMORY_SAVE,            // 保存记忆

        // ========== 代码索引/搜索 ==========
        INDEX_CODE,             // 索引代码
        SEARCH_CODE,            // 搜索代码
        GRAPH_QUERY,            // 图谱查询

        // ========== 快照管理 ==========
        SNAPSHOT,               // 快照列表
        RESTORE_SNAPSHOT,       // 恢复快照

        // ========== MCP管理 ==========
        MCP_LIST,               // MCP列表
        MCP_RESTART,            // 重启MCP
        MCP_LOGS,               // 查看日志
        MCP_DISABLE,            // 禁用MCP
        MCP_ENABLE,             // 启用MCP
        MCP_RESOURCES,          // MCP资源
        MCP_PROMPTS,            // MCP提示

        // ========== 外部连接 ==========
        BROWSER,                // 浏览器控制
        WECHAT,                 // 微信通道控制

        // ========== 任务管理 ==========
        TASK,                   // 任务管理

        // ========== 主动对话（新增）==========
        PROACTIVE_STATUS,       // 查看主动对话状态
        PROACTIVE_MUTE,         // 静音N小时
        PROACTIVE_UNMUTE,       // 恢复常态

        // ========== 角色/人格（新增）==========
        PERSONA,                // 查看当前角色设定
        LIFE_PLAN,              // 查看人生计划
        CHARACTER_CREATE,       // 创建角色
        CHARACTER_SWITCH,       // 切换角色
        CHARACTER_LIST,         // 角色列表

        // ========== 内心独白（新增）==========
        INNER_MONOLOGUE,        // 内心独白设置

        // ========== Skill系统 ==========
        SKILL_LIST,             // Skill列表
        SKILL_SHOW,             // 查看Skill
        SKILL_ON,               // 启用Skill
        SKILL_OFF,              // 禁用Skill
        SKILL_RELOAD,           // 重载Skill

        // ========== 其他 ==========
        INIT_PROJECT_MEMORY,    // 初始化项目记忆
        CONTEXT_STATUS,         // 上下文状态
        POLICY_STATUS,          // 策略状态
        AUDIT_TAIL,             // 审计日志
        CONFIG,                 // 配置管理
        EXPORT                  // 导出数据
    }

    record ParsedCommand(CommandType type, String payload) {
        static ParsedCommand none() {
            return new ParsedCommand(CommandType.NONE, null);
        }
    }

    private CliCommandParser() {
    }

    static ParsedCommand parse(String input) {
        if (input == null) {
            return ParsedCommand.none();
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return ParsedCommand.none();
        }

        if (trimmed.equalsIgnoreCase("/exit")
                || trimmed.equalsIgnoreCase("/quit")
                || trimmed.equalsIgnoreCase("exit")
                || trimmed.equalsIgnoreCase("quit")) {
            return new ParsedCommand(CommandType.EXIT, null);
        }

        if (trimmed.equalsIgnoreCase("/cancel") || trimmed.equalsIgnoreCase("cancel")) {
            return new ParsedCommand(CommandType.CANCEL, null);
        }

        if (trimmed.equalsIgnoreCase("/clear") || trimmed.equalsIgnoreCase("clear")) {
            return new ParsedCommand(CommandType.CLEAR, null);
        }

        if (trimmed.equalsIgnoreCase("/compact")) {
            return new ParsedCommand(CommandType.COMPACT, null);
        }

        if (trimmed.equalsIgnoreCase("/history clear")) {
            return new ParsedCommand(CommandType.HISTORY_CLEAR, null);
        }

        if (trimmed.equalsIgnoreCase("/init")) {
            return new ParsedCommand(CommandType.INIT_PROJECT_MEMORY, null);
        }

        if (trimmed.regionMatches(true, 0, "/init ", 0, 6)) {
            return new ParsedCommand(CommandType.INIT_PROJECT_MEMORY, trimmed.substring(6).trim());
        }

        if (trimmed.equalsIgnoreCase("/model")) {
            return new ParsedCommand(CommandType.SWITCH_MODEL, null);
        }

        if (trimmed.regionMatches(true, 0, "/model ", 0, 7)) {
            return new ParsedCommand(CommandType.SWITCH_MODEL, trimmed.substring(7).trim());
        }

        if (trimmed.equalsIgnoreCase("/plan")) {
            return new ParsedCommand(CommandType.SWITCH_PLAN, null);
        }

        if (trimmed.regionMatches(true, 0, "/plan ", 0, 6)) {
            return new ParsedCommand(CommandType.SWITCH_PLAN, trimmed.substring(6).trim());
        }

        if (trimmed.equalsIgnoreCase("/team")) {
            return new ParsedCommand(CommandType.SWITCH_TEAM, null);
        }

        if (trimmed.regionMatches(true, 0, "/team ", 0, 6)) {
            return new ParsedCommand(CommandType.SWITCH_TEAM, trimmed.substring(6).trim());
        }

        if (trimmed.equals("/管理") || trimmed.equalsIgnoreCase("/manage")
                || trimmed.equalsIgnoreCase("/退出管理")) {
            return new ParsedCommand(CommandType.SWITCH_MANAGE, null);
        }

        if (trimmed.equals("/定时任务") || trimmed.equals("/任务列表")) {
            return new ParsedCommand(CommandType.TASK, "scheduled");
        }

        if (trimmed.regionMatches(true, 0, "/任务运行 ", 0, 6)) {
            return new ParsedCommand(CommandType.TASK, "run " + trimmed.substring(6).trim());
        }

        if (trimmed.regionMatches(true, 0, "/task run ", 0, 9)) {
            return new ParsedCommand(CommandType.TASK, "run " + trimmed.substring(9).trim());
        }

        if (trimmed.equalsIgnoreCase("/hitl on")) {
            return new ParsedCommand(CommandType.SWITCH_HITL, "on");
        }

        if (trimmed.equalsIgnoreCase("/hitl off")) {
            return new ParsedCommand(CommandType.SWITCH_HITL, "off");
        }

        if (trimmed.equalsIgnoreCase("/hitl")) {
            return new ParsedCommand(CommandType.SWITCH_HITL, null);
        }

        if (trimmed.equalsIgnoreCase("/memory") || trimmed.equalsIgnoreCase("/mem")) {
            return new ParsedCommand(CommandType.MEMORY_STATUS, null);
        }

        if (trimmed.equalsIgnoreCase("/memory clear") || trimmed.equalsIgnoreCase("/mem clear")) {
            return new ParsedCommand(CommandType.MEMORY_CLEAR, null);
        }

        if (trimmed.equalsIgnoreCase("/memory list") || trimmed.equalsIgnoreCase("/mem list")) {
            return new ParsedCommand(CommandType.MEMORY_LIST, null);
        }

        if (trimmed.regionMatches(true, 0, "/memory delete ", 0, 15)) {
            return new ParsedCommand(CommandType.MEMORY_DELETE, trimmed.substring(15).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mem delete ", 0, 12)) {
            return new ParsedCommand(CommandType.MEMORY_DELETE, trimmed.substring(12).trim());
        }

        if (trimmed.regionMatches(true, 0, "/memory search ", 0, 15)) {
            return new ParsedCommand(CommandType.MEMORY_SEARCH, trimmed.substring(15).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mem search ", 0, 12)) {
            return new ParsedCommand(CommandType.MEMORY_SEARCH, trimmed.substring(12).trim());
        }

        if (trimmed.equalsIgnoreCase("/save")) {
            return new ParsedCommand(CommandType.MEMORY_SAVE, null);
        }

        if (trimmed.regionMatches(true, 0, "/save ", 0, 6)) {
            return new ParsedCommand(CommandType.MEMORY_SAVE, trimmed.substring(6).trim());
        }

        if (trimmed.equalsIgnoreCase("/index")) {
            return new ParsedCommand(CommandType.INDEX_CODE, null);
        }

        if (trimmed.regionMatches(true, 0, "/index ", 0, 7)) {
            return new ParsedCommand(CommandType.INDEX_CODE, trimmed.substring(7).trim());
        }

        if (trimmed.equalsIgnoreCase("/search")) {
            return new ParsedCommand(CommandType.SEARCH_CODE, null);
        }

        if (trimmed.regionMatches(true, 0, "/search ", 0, 8)) {
            return new ParsedCommand(CommandType.SEARCH_CODE, trimmed.substring(8).trim());
        }

        if (trimmed.equalsIgnoreCase("/graph")) {
            return new ParsedCommand(CommandType.GRAPH_QUERY, null);
        }

        if (trimmed.regionMatches(true, 0, "/graph ", 0, 7)) {
            return new ParsedCommand(CommandType.GRAPH_QUERY, trimmed.substring(7).trim());
        }

        if (trimmed.equalsIgnoreCase("/context") || trimmed.equalsIgnoreCase("/ctx")) {
            return new ParsedCommand(CommandType.CONTEXT_STATUS, null);
        }

        if (trimmed.equalsIgnoreCase("/policy")) {
            return new ParsedCommand(CommandType.POLICY_STATUS, null);
        }

        if (trimmed.equalsIgnoreCase("/config")) {
            return new ParsedCommand(CommandType.CONFIG, null);
        }

        if (trimmed.regionMatches(true, 0, "/config ", 0, 8)) {
            return new ParsedCommand(CommandType.CONFIG, trimmed.substring(8).trim());
        }

        if (trimmed.equalsIgnoreCase("/audit")) {
            return new ParsedCommand(CommandType.AUDIT_TAIL, null);
        }

        if (trimmed.regionMatches(true, 0, "/audit ", 0, 7)) {
            return new ParsedCommand(CommandType.AUDIT_TAIL, trimmed.substring(7).trim());
        }

        if (trimmed.equalsIgnoreCase("/snapshot")) {
            return new ParsedCommand(CommandType.SNAPSHOT, "list");
        }

        if (trimmed.regionMatches(true, 0, "/snapshot ", 0, 10)) {
            return new ParsedCommand(CommandType.SNAPSHOT, trimmed.substring(10).trim());
        }

        if (trimmed.equalsIgnoreCase("/restore")) {
            return new ParsedCommand(CommandType.RESTORE_SNAPSHOT, null);
        }

        if (trimmed.regionMatches(true, 0, "/restore ", 0, 9)) {
            return new ParsedCommand(CommandType.RESTORE_SNAPSHOT, trimmed.substring(9).trim());
        }

        if (trimmed.equalsIgnoreCase("/browser")) {
            return new ParsedCommand(CommandType.BROWSER, "status");
        }

        if (trimmed.regionMatches(true, 0, "/browser ", 0, 9)) {
            return new ParsedCommand(CommandType.BROWSER, trimmed.substring(9).trim());
        }

        if (trimmed.equalsIgnoreCase("/wechat")) {
            return new ParsedCommand(CommandType.WECHAT, "start");
        }

        if (trimmed.regionMatches(true, 0, "/wechat ", 0, 8)) {
            return new ParsedCommand(CommandType.WECHAT, trimmed.substring(8).trim());
        }

        if (trimmed.equalsIgnoreCase("/task")) {
            return new ParsedCommand(CommandType.TASK, "list");
        }

        if (trimmed.regionMatches(true, 0, "/task ", 0, 6)) {
            return new ParsedCommand(CommandType.TASK, trimmed.substring(6).trim());
        }

        // 主动对话相关命令
        if (trimmed.equalsIgnoreCase("/主动状态") || trimmed.equalsIgnoreCase("/proactive")) {
            return new ParsedCommand(CommandType.PROACTIVE_STATUS, null);
        }

        if (trimmed.regionMatches(true, 0, "/静音 ", 0, 5)) {
            String hours = trimmed.substring(5).trim();
            return new ParsedCommand(CommandType.PROACTIVE_MUTE, hours);
        }

        if (trimmed.equalsIgnoreCase("/常态") || trimmed.equalsIgnoreCase("/unmute")) {
            return new ParsedCommand(CommandType.PROACTIVE_UNMUTE, null);
        }

        // 角色/人生计划相关命令
        if (trimmed.equalsIgnoreCase("/人格") || trimmed.equalsIgnoreCase("/persona")) {
            return new ParsedCommand(CommandType.PERSONA, null);
        }

        if (trimmed.equalsIgnoreCase("/人生计划") || trimmed.equalsIgnoreCase("/life")) {
            return new ParsedCommand(CommandType.LIFE_PLAN, null);
        }

        if (trimmed.regionMatches(true, 0, "/创建角色 ", 0, 5)) {
            return new ParsedCommand(CommandType.CHARACTER_CREATE, trimmed.substring(5).trim());
        }

        if (trimmed.regionMatches(true, 0, "/切换角色 ", 0, 5)) {
            return new ParsedCommand(CommandType.CHARACTER_SWITCH, trimmed.substring(5).trim());
        }

        if (trimmed.equalsIgnoreCase("/角色列表") || trimmed.equalsIgnoreCase("/characters")) {
            return new ParsedCommand(CommandType.CHARACTER_LIST, null);
        }

        // 内心独白命令
        if (trimmed.equalsIgnoreCase("/内心独白") || trimmed.equalsIgnoreCase("/os")) {
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, null);
        }

        if (trimmed.equalsIgnoreCase("/内心独白 开启") || trimmed.equalsIgnoreCase("/os on")) {
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, "on");
        }

        if (trimmed.equalsIgnoreCase("/内心独白 关闭") || trimmed.equalsIgnoreCase("/os off")) {
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, "off");
        }

        if (trimmed.regionMatches(true, 0, "/内心独白 频率 ", 0, 7) || trimmed.regionMatches(true, 0, "/os freq ", 0, 9)) {
            String payload = trimmed.startsWith("/内心独白") ? trimmed.substring(7).trim() : trimmed.substring(9).trim();
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, "freq " + payload);
        }

        if (trimmed.regionMatches(true, 0, "/内心独白 触发 添加 ", 0, 9) || trimmed.regionMatches(true, 0, "/os trigger add ", 0, 16)) {
            String payload = trimmed.startsWith("/内心独白") ? trimmed.substring(9).trim() : trimmed.substring(16).trim();
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, "trigger add " + payload);
        }

        if (trimmed.regionMatches(true, 0, "/内心独白 触发 删除 ", 0, 9) || trimmed.regionMatches(true, 0, "/os trigger del ", 0, 16)) {
            String payload = trimmed.startsWith("/内心独白") ? trimmed.substring(9).trim() : trimmed.substring(16).trim();
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, "trigger del " + payload);
        }

        if (trimmed.equalsIgnoreCase("/内心独白 触发 清空") || trimmed.equalsIgnoreCase("/os trigger clear")) {
            return new ParsedCommand(CommandType.INNER_MONOLOGUE, "trigger clear");
        }

        if (trimmed.equalsIgnoreCase("/skill") || trimmed.equalsIgnoreCase("/skill list")) {
            return new ParsedCommand(CommandType.SKILL_LIST, null);
        }

        if (trimmed.equalsIgnoreCase("/skill reload")) {
            return new ParsedCommand(CommandType.SKILL_RELOAD, null);
        }

        if (trimmed.regionMatches(true, 0, "/skill show ", 0, 12)) {
            return new ParsedCommand(CommandType.SKILL_SHOW, trimmed.substring(12).trim());
        }

        if (trimmed.regionMatches(true, 0, "/skill on ", 0, 10)) {
            return new ParsedCommand(CommandType.SKILL_ON, trimmed.substring(10).trim());
        }

        if (trimmed.regionMatches(true, 0, "/skill off ", 0, 11)) {
            return new ParsedCommand(CommandType.SKILL_OFF, trimmed.substring(11).trim());
        }

        if (trimmed.equalsIgnoreCase("/export")) {
            return new ParsedCommand(CommandType.EXPORT, null);
        }

        if (trimmed.equalsIgnoreCase("/mcp")) {
            return new ParsedCommand(CommandType.MCP_LIST, null);
        }

        if (trimmed.regionMatches(true, 0, "/mcp resources ", 0, 15)) {
            return new ParsedCommand(CommandType.MCP_RESOURCES, trimmed.substring(15).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mcp prompts ", 0, 13)) {
            return new ParsedCommand(CommandType.MCP_PROMPTS, trimmed.substring(13).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mcp restart ", 0, 13)) {
            return new ParsedCommand(CommandType.MCP_RESTART, trimmed.substring(13).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mcp logs ", 0, 10)) {
            return new ParsedCommand(CommandType.MCP_LOGS, trimmed.substring(10).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mcp disable ", 0, 13)) {
            return new ParsedCommand(CommandType.MCP_DISABLE, trimmed.substring(13).trim());
        }

        if (trimmed.regionMatches(true, 0, "/mcp enable ", 0, 12)) {
            return new ParsedCommand(CommandType.MCP_ENABLE, trimmed.substring(12).trim());
        }

        if (trimmed.startsWith("/")) {
            return new ParsedCommand(CommandType.UNKNOWN_COMMAND, trimmed);
        }

        return ParsedCommand.none();
    }
}
