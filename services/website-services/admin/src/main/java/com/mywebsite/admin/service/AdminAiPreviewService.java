package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AdminAiPreviewService {

    SseEmitter previewStream(long siteId, AdminAiPreviewChatRequest request);
}
