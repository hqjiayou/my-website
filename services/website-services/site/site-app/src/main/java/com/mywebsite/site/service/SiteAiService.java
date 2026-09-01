package com.mywebsite.site.service;

import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SiteAiService {

    SiteAiChatResponseDto chat(long siteId, SiteAiChatRequestDto request);

    SseEmitter chatStream(long siteId, SiteAiChatRequestDto request);
}
