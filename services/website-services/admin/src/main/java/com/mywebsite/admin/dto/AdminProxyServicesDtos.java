package com.mywebsite.admin.dto;

import java.util.List;
import java.util.Map;

public final class AdminProxyServicesDtos {

    private AdminProxyServicesDtos() {}

    /** 不含管理密钥与客户端 key。accountPacing 来自代理管理 API 的生效值；拿不到时为 null。 */
    public record AdminProxyServicesStatusDto(
            String name,
            String baseUrl,
            boolean reachable,
            boolean managementConfigured,
            List<AdminProxyServicesAccountDto> accounts,
            AdminProxyServicesAccountPacingDto accountPacing) {}

    /**
     * 代理进程里正在生效的按账号 pacing。某一项为 0 表示该闸关闭。
     * 数值来自管理 API，不读配置文件。
     */
    public record AdminProxyServicesAccountPacingDto(
            long minIntervalMs,
            long minIntervalJitterMs,
            int maxInFlight,
            int quotaUsedPercent,
            long maxWaitMs) {}

    /**
     * success / failed 是这次进程启动后的次数。usedPercent、modelUsedPercent 只在上游有额度观察时有值。
     * email 可能为空。
     */
    public record AdminProxyServicesAccountDto(
            String name,
            String provider,
            boolean disabled,
            boolean unavailable,
            String email,
            long success,
            long failed,
            Integer usedPercent,
            Map<String, Integer> modelUsedPercent) {}

    /** CLIProxyAPI {@code access.api-keys}。这是调用代理的客户端 key，不是上游模型密钥。 */
    public record AdminProxyServicesApiKeysDto(List<String> apiKeys) {}

    public record AdminProxyServicesApiKeyWriteRequest(String apiKey) {}

    public record AdminProxyServicesApiKeyCreatedDto(String apiKey) {}

    /** 只含授权链接和会话 state，不含管理密钥与令牌。 */
    public record AdminProxyServicesLoginDto(String provider, String url, String state) {}

    public record AdminProxyServicesLoginRequest(String provider) {}

    /** status 为 wait、ok 或 error。error 仅在失败时有短文本。 */
    public record AdminProxyServicesLoginStatusDto(String status, String error) {}

    /**
     * 对外只有 apiBaseUrl 这一个地址。accounts 是已登录账号及其放出的模型名，不含密钥。
     * callable 表示至少有一个可用账号放出了模型，并且已经有客户端 key。
     */
    public record AdminProxyServicesUsageDto(
            String apiBaseUrl, boolean callable, List<AdminProxyServicesPublishedAccountDto> accounts) {}

    public record AdminProxyServicesPublishedAccountDto(
            String name, String provider, boolean disabled, boolean unavailable, List<String> modelIds) {}
}
