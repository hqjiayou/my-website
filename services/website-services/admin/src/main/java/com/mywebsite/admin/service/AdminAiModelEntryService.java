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
    AgentModelSelection currentSelection(long agentId);

    /**
     * 生效链：首元是 {@link #currentSelection(long)} 的主用入口，其后依次是同站其他启用入口、
     * 全站其他启用入口（各组内默认在前、id 升序）。主用不存在（无默认，或默认解密不出 key）时返回空列表，
     * 备援不得越过坏主用直接顶上。解密不出 key 的备援跳过。
     */
    List<AgentModelSelection> selectionChain(long agentId);
}
