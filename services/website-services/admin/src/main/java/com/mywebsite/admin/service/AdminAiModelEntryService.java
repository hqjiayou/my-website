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
     * 与 {@link #selectionChain(long, String)} 同一条链，只给菜单：id、名称、spec，不含 key。
     */
    List<ChainCandidate> chainCandidates(long agentId, String mode);

    /**
     * 链上这条入口的凭证。不在链上时抛 {@code MODEL_ENTRY_UNAVAILABLE}，调用方不得改用链首。
     */
    AgentModelSelection selectionOnChain(long agentId, String mode, long entryId);

    /**
     * 菜单上的一条候选。与生效链同一顺序。
     */
    record ChainCandidate(Long id, String displayName, String modelSpec) {}

    /**
     * 已登记的嵌入类：站点启用的 KEY 入口里带 embedding spec 且能解密的，再接全站同样的入口。
     * 同一模型名只留先出现的一把，{@code google-gla:} 与 {@code google:} 算同一类，最多 4 类。
     * CLI 入口不参与。都没有时返回空列表，检索改用进程嵌入模型。
     */
    List<AgentModelSelection> embeddingClasses(long agentId);

    /**
     * {@link #embeddingClasses(long)} 的第一类。都没有时返回 null。
     * 单类调用方仍用这一把，和以前「第一把 KEY embedding」一致。
     */
    AgentModelSelection embeddingSelection(long agentId);
}
