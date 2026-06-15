package com.paicli.llm;

/**
 * SiliconFlow（硅基流动）客户端。
 * SiliconFlow 提供 OpenAI 兼容的 API，支持多种模型（DeepSeek、Qwen 等）。
 */
public class SiliconFlowClient extends AbstractOpenAiCompatibleClient {

    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-ai/DeepSeek-V4-Flash";

    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public SiliconFlowClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public SiliconFlowClient(String apiKey, String model) {
        this(apiKey, model, API_URL);
    }

    SiliconFlowClient(String apiKey, String model, String apiUrl) {
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl : API_URL;
    }

    @Override
    protected String getApiUrl() {
        return apiUrl;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProviderName() {
        return "siliconflow";
    }

    @Override
    public int maxContextWindow() {
        // DeepSeek-V4-Flash 支持 1M 上下文
        return 1_000_000;
    }

    @Override
    public boolean supportsPromptCaching() {
        return true;
    }

    @Override
    public String promptCacheMode() {
        return "automatic-prefix-cache";
    }
}
