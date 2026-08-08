package com.mywebsite.site.service;

import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;

public interface SiteAiService {

    SiteAiChatResponseDto chat(long siteId, SiteAiChatRequestDto request);
}
