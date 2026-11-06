package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetUpdateRequest;
import java.util.List;

/**
 * 对话链的两个归属面（Issue #214 UoW-3 链内置化）：
 *
 * <ul>
 *   <li><b>链模板库</b>（原 #210 链预设）：命名的有序模型入口集合，只作复用素材。
 *       套用到 agent 时是<b>拷贝</b>——之后改模板不影响已套用的 agent。模板不再被
 *       agent 引用，可自由删除。</li>
 *   <li><b>agent 自有链</b>：链的生效形态，存 agent 行的两个字段（按模式各一份
 *       有序 entry_ids，V69）。正式对话、试聊、转写、提炼的组链都只读自有链；
 *       原 {@code chain_preset_id} 引用自本步起停止读写（列保留待 UoW-7 评估）。</li>
 * </ul>
 */
public interface AdminAiChainPresetService {

    List<AdminAiChainPresetDto> listPresets();

    AdminAiChainPresetDto getPreset(long id);

    AdminAiChainPresetDto createPreset(AdminAiChainPresetCreateRequest request);

    AdminAiChainPresetDto updatePreset(long id, AdminAiChainPresetUpdateRequest request);

    /** 软删模板；模板不再被 agent 引用（套用是拷贝），无引用拦截。 */
    void deletePreset(long id);

    /** agent 的自有链全貌（两模式的成员 id 名单；空列表＝该模式未配链）。 */
    AdminAgentChainDto getAgentChain(long agentId);

    /**
     * 整表保存某模式的自有链：成员须全部存在、且入口模式与目标链模式一致
     * （入口 mode 空值按 KEY）。保存后该模式链即以新名单为准。
     */
    AdminAgentChainDto updateAgentChain(long agentId, String mode, List<Long> entryIds);

    /**
     * 从模板载入：模板成员中属于 {@code mode} 的部分按模板序拷入该模式自有链
     * （整表覆盖该模式链；另一模式不动）。拷贝完成后与模板脱钩。
     */
    AdminAgentChainDto loadAgentChainFromTemplate(long agentId, String mode, long templateId);

    /** 把某模式自有链的当前内容另存为一条新模板，返回新建模板。 */
    AdminAiChainPresetDto saveAgentChainAsTemplate(long agentId, String mode, String name);

    /**
     * 组链读路径：agent 自有链在给定模式下的有序成员入口 id。{@code mode} 为
     * null 表示整链，定义为 KEY 链在前、CLI 链在后拼接（链内置化前两模式同存
     * 一份预设名单、交错序即预设序；拆分存储后整链序以本定义为准——生产路径
     * 自 #208 起均显式传模式，整链仅剩兼容面）。agent 不存在或未配链时为空。
     */
    List<Long> ownChainEntryIds(long agentId, String mode);
}
