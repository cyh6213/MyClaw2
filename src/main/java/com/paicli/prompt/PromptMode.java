package com.paicli.prompt;

public enum PromptMode {
    CHAT(""),
    MANAGE("modes/agent.md"),
    AGENT("modes/agent.md"),
    PLAN("modes/plan.md"),
    PLANNER("modes/planner.md"),
    TEAM_PLANNER("modes/team-planner.md"),
    TEAM_WORKER("modes/team-worker.md"),
    TEAM_REVIEWER("modes/team-reviewer.md");

    private final String resourcePath;

    PromptMode(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String resourcePath() {
        return resourcePath;
    }
}
