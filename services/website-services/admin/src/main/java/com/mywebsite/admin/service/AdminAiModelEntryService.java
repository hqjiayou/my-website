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
     * agent 对话链的主用入口（链首）：链由该 agent 引用的对话链预设决定
     * （Issue #210 UoW-7）。未引用预设、或预设内无可用主用时返回 null。
     */
    AgentModelSelection currentSelection(long agentId);

    /**
     * agent 的对话生效链（Issue #210 UoW-7）：取该 agent 引用的链预设快照
     * （有序 entry ids），按预设序取启用入口交 {@code ModelChainResolver} 既有
     * 规则组链——首元是主用（预设序内第一个默认入口），其后按预设序备援；
     * 主用不存在（预设内无默认，或默认解密不出 key）时返回空列表，备援不得
     * 越过坏主用直接顶上；解密不出 key 的备援跳过；已删除/停用的成员不入链。
     * 未引用预设时为空列表。不分模式。
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
     * 已登记的嵌入类（Issue #126 登记制；scope 于 Issue #209 UoW-3 改知识
     * 空间）：来源是登记表，空间登记在前、全站登记在后，同一模型名只留先
     * 出现的一把，{@code google-gla:} 与 {@code google:} 算同一类，最多
     * 4 类。实现委托 agent-core 的 {@code AgentEmbeddingClassService}
     * （含首次读物化），本接口只是 admin 侧的门面。都没有时返回空列表，
     * 检索改用进程嵌入模型。
     */
    List<AgentModelSelection> embeddingClassesForSpace(long baseId);
}
