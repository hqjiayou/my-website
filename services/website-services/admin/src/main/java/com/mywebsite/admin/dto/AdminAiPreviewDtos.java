package com.mywebsite.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AdminAiPreviewDtos {

    private AdminAiPreviewDtos() {}

    /**
     * 后台预览 chat：不落库。history 仅用于当前预览线程上下文；
     * {@code promptProfile} 仍由 Java 从站点配置注入，不接受前端直传。
     */
    public record AdminAiPreviewChatRequest(
            @NotBlank @Size(max = 4000) String message,
            @Size(max = 20) List<@Valid AdminAiPreviewHistoryMessage> history) {}

    public record AdminAiPreviewHistoryMessage(
            @NotBlank @Size(max = 16) String role, @NotBlank @Size(max = 8000) String content) {}
}
