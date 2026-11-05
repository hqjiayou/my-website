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
     * 平台链的主用入口（链首）。入口平台化后（Issue #210 UoW-6）链与 agent 无关，
     * {@code agentId} 参数仅为兼容既有调用面保留、不参与组链。都没有时返回 null。
     */
    AgentModelSelection currentSelection(long agentId);

    /**
     * 平台生效链：与请求方 agent 无关（Issue #210 UoW-6 起入口全平台注册）。
     * 首元是主用入口，其后依次是带来源标注的其他启用入口、平台原生的其他启用入口
     * （各层内默认在前、id 升序）——来源层级保证单 agent 形态下与平台化前逐条等价，
     * 链预设（UoW-7）落地后由预设快照取代。主用不存在（无默认，或默认解密不出 key）
     * 时返回空列表，备援不得越过坏主用直接顶上。解密不出 key 的备援跳过。不分模式。
     * {@code agentId} 参数仅为兼容既有调用面保留（UoW-7 收口）。
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
     * 已登记的嵌入类（Issue #126 登记制）：来源是登记表，站点登记在前、全站
     * 登记在后，同一模型名只留先出现的一把，{@code google-gla:} 与
     * {@code google:} 算同一类，最多 4 类。实现委托 agent-core 的
     * {@code AgentEmbeddingClassService}（含首次读物化），本接口只是 admin 侧
     * 的门面。都没有时返回空列表，检索改用进程嵌入模型。
     */
    List<AgentModelSelection> embeddingClasses(long agentId);
}
