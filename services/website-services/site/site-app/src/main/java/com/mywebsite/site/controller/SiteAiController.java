package com.mywebsite.site.controller;

import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import com.mywebsite.site.service.SiteAiService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site")
public class SiteAiController {

    private final SiteAiService siteAiService;

    public SiteAiController(SiteAiService siteAiService) {
        this.siteAiService = siteAiService;
    }

    @PostMapping("/{siteId}/ai/chat")
    public ApiResponse<SiteAiChatResponseDto> chat(
            @PathVariable("siteId") long siteId, @RequestBody SiteAiChatRequestDto request) {
        return ApiResponse.success(siteAiService.chat(siteId, request));
    }
}
