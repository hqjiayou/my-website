package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassDto;
import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassSaveRequest;
import java.util.List;

/**
 * 嵌入类登记（Issue #126 UoW-1）：登记表 {@code agent_embedding_class} 是嵌入类的
 * 唯一编辑面。登记行只引用模型入口，类的 spec/key/baseUrl 运行时从入口解析，
 * 本服务不读不存任何 key。
 *
 * <p>scope 语义：{@code agentId} 为 null＝全站 scope，非空＝该站点 scope。
 * 读写的都是该 scope 自己的登记行；全站与站点的合并（站点优先）是消费侧
 * （UoW-2 来源切换）的事，不在本服务。
 */
public interface AdminEmbeddingRegistrationService {

    /** 某 scope 的登记行（按 sort_order 升序），附从入口解析出的展示字段。 */
    List<AdminEmbeddingClassDto> listRegistrations(Long agentId);

    /**
     * 整表保存：以提交的有序 entry 清单整体替换该 scope 的登记（事务内完成）。
     * 校验（任一不过整表拒绝、不落库）：入口须存在、mode=KEY 且 embeddingSpec 非空；
     * 同一入口不可重复；同 scope 内规范化类键不可重复；总数不得超过
     * {@code AgentEmbeddingClassUtils.MAX_EMBEDDING_CLASSES}（拒绝，不截断）。
     */
    List<AdminEmbeddingClassDto> replaceRegistrations(Long agentId, AdminEmbeddingClassSaveRequest request);
}
