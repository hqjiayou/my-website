package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentExperienceLessonDraft;
import com.mywebsite.agent.dto.AgentExperienceLessonRevision;
import com.mywebsite.agent.entity.AgentExperienceLessonEntity;
import java.util.List;

/**
 * 经验沉淀条目的状态机（Issue #184 UoW-1）：创建候选与审核流转的唯一入口，
 * admin（提炼落库 UoW-2、人审 UoW-3）与 agent-app（已批准条目下发 UoW-4）共用
 * 这一份实现，不在各模块另写。
 *
 * <p>状态机（非法流转抛 {@code BusinessException(CONFLICT)}，条目不存在抛
 * {@code NOT_FOUND}）：
 *
 * <pre>
 *   （新建） ──→ PENDING ──approve──→ APPROVED ──disable──→ DISABLED
 *                  │
 *                  └──reject──→ REJECTED
 * </pre>
 *
 * <p>批准时分配 L 编号：现有最大编号 +1，从 L6 起（L1–L5 是 EXPERIENCE.md
 * 历史存档）；停用不回收编号，编号永不重用。未批准条目不经任何通道下发。
 */
public interface AgentExperienceLessonService {

    /** 新建候选（status=PENDING，不分配 L 编号）；title／lesson 为空时抛 BAD_REQUEST。 */
    AgentExperienceLessonEntity createCandidate(AgentExperienceLessonDraft draft);

    /**
     * 批准一条 PENDING 候选：先按 {@code revision} 应用人审修订（null 字段不改），
     * 再分配 L 编号并置 APPROVED。{@code revision} 本身为 null 时按无修订处理。
     */
    AgentExperienceLessonEntity approve(long id, AgentExperienceLessonRevision revision);

    /** 驳回一条 PENDING 候选（置 REJECTED），不分配 L 编号。 */
    AgentExperienceLessonEntity reject(long id);

    /** 停用一条 APPROVED 条目（置 DISABLED）；L 编号保留、不回收。 */
    AgentExperienceLessonEntity disable(long id);

    /** 按状态取条目（审核队列用），创建时间升序；status 非法时抛 BAD_REQUEST。 */
    List<AgentExperienceLessonEntity> listByStatus(String status);

    /** 全部已批准条目，按 L 编号升序（UoW-4 请求注入的下发来源）。 */
    List<AgentExperienceLessonEntity> listApproved();
}
