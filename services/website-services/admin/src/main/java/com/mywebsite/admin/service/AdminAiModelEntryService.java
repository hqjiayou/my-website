package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.agent.dto.AgentModelSelection;
import java.util.List;

public interface AdminAiModelEntryService {

    List<AdminAiModelEntryDto> listEntries();

    AdminAiModelEntryDto createEntry(AdminAiModelEntryWriteRequest request);

    AdminAiModelEntryDto updateEntry(long id, AdminAiModelEntryWriteRequest request);

    void deleteEntry(long id);

    /**
     * 该站点启用的默认入口；没有则用全站启用的默认入口。都没有时返回 null。
     */
    AgentModelSelection currentSelection(long siteId);
}
