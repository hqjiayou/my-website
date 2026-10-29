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
     * 备援不得越过坏主用直接顶上。解密不出 key 的备援跳过。不分模式。
     */
    List<AgentModelSelection> selectionChain(long agentId);

    /**
     * 与 {@link #selectionChain(long)} 同规则。{@code mode} 为 CLI 或 KEY 时只保留该模式，备援封闭在模式内；
     * 为空时即整链。
     */
    List<AgentModelSelection> selectionChain(long agentId, String mode);

    /**
     * 向量索引用的凭证，与当前对话模式无关。站点 KEY 入口里第一把带 embedding spec 且能解密的 key，
     * 否则全站同样的入口。都没有时返回 null，检索改用进程嵌入模型。CLI 入口不参与。
     */
    AgentModelSelection embeddingSelection(long agentId);
}
