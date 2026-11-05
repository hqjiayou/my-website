package com.mywebsite.agent.utils;

import com.mywebsite.agent.dto.AgentModelSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 模型入口组链的唯一实现（Issue #132）。此前 admin 的
 * {@code AdminAiModelEntryServiceImpl.selectionChain} 与 agent-app 的
 * {@code AgentAiServiceImpl.loadModelChain} 两处手写同规则、靠注释互保一致；
 * 现在规则只在这里，两侧把各自实体映射成 {@link EntrySnapshot} 后调用，
 * 实体与 Mapper 仍归各模块（同表双映射不在本单范围）。
 *
 * <p>嵌入类不在这里：原先扫入口 {@code embeddingSpec} 的推算已随 Issue #126
 * 登记制退役，唯一读取入口是 {@code AgentEmbeddingClassService}（agent-core
 * service 层）。链上每条 selection 仍带入口自己的 embeddingSpec 字段
 * （CLI 清空），那是入口属性、不是嵌入类来源。
 *
 * <p>纯静态、无状态：解密由调用方完成并把明文 key 放进快照（解不出放 null）。
 * 输入列表的顺序即组链顺序：两层形态下调用方按「默认在前、id 升序」查好再传入；
 * 链预设形态（{@link #resolvePresetChain}，Issue #210 UoW-7 起的生产路径）下
 * 顺序就是预设的成员顺序。
 *
 * <p>网关 {@code /v1} 的 alias 主用链不在这里：它以 alias 点名入口为主用、
 * 规则不同，继续走 {@code GatewayV1ServiceImpl} 自己的组装。
 */
public final class ModelChainResolver {

    private ModelChainResolver() {}

    /**
     * 一条模型入口的中立快照。{@code apiKey} 为已解密明文（解不出为 null）；
     * {@code mode} 为原始列值（归一化在本类做）；{@code embeddingSpec} 为原始列值。
     */
    public record EntrySnapshot(
            Long id,
            String displayName,
            String modelSpec,
            String apiKey,
            String baseUrl,
            String embeddingSpec,
            boolean defaultEntry,
            String mode) {}

    /** 组链结果的一条：带入口 id 与展示名（菜单/记账归属用）＋转发凭证。 */
    public record ResolvedLink(Long id, String displayName, AgentModelSelection selection) {}

    /** 入口模式归属：空值按 KEY（与 V35 列默认同口径）。 */
    public static String entryMode(String rawMode) {
        return StringUtils.hasText(rawMode) ? rawMode.trim().toUpperCase(Locale.ROOT) : "KEY";
    }

    /**
     * 生效链（两层形态，规则本体）：先按 {@code mode} 过滤（null 表示整链、不过滤）；
     * 主用＝优先层第一个默认入口，否则平台层第一个默认；主用不存在或其凭证组装不出
     * （无 spec／无 key）时整链为空——备援不得越过坏主用顶上（ADR-0004）；其后依次是
     * 优先层其余、平台层其余（保持输入顺序），凭证组装不出的备援跳过。
     *
     * <p>两层的划分曾按入口来源（UoW-6 的过渡组链）；自 UoW-7 起生产组链改走
     * {@link #resolvePresetChain}（链预设快照），来源分层整体退役、本方法只作
     * 规则引擎与等价基线保留。网关 /v1 不走这里（见类注释）。
     */
    public static List<ResolvedLink> resolveChain(
            List<EntrySnapshot> priorityScope, List<EntrySnapshot> platformScope, String mode) {
        List<EntrySnapshot> agentEntries = filterMode(priorityScope, mode);
        List<EntrySnapshot> globalEntries = filterMode(platformScope, mode);
        EntrySnapshot primary = firstDefault(agentEntries);
        if (primary == null) {
            primary = firstDefault(globalEntries);
        }
        if (primary == null) {
            return List.of();
        }
        AgentModelSelection primarySelection = toSelection(primary);
        if (primarySelection == null) {
            return List.of();
        }
        List<ResolvedLink> chain = new ArrayList<>();
        chain.add(linkOf(primary, primarySelection));
        appendLinks(chain, agentEntries, primary.id());
        appendLinks(chain, globalEntries, primary.id());
        return List.copyOf(chain);
    }

    /**
     * 链预设组链（Issue #210 UoW-7）：{@code members} 是 agent 引用的对话链预设
     * 成员快照，按预设顺序排列（调用方已按预设 entry ids 取入口并过滤停用/已删）。
     * 规则与两层形态同一份：按 {@code mode} 过滤后，预设序内第一个默认入口作主用，
     * 主用不存在或凭证组装不出时整链为空；其后按预设序依次备援，凭证组装不出的跳过。
     * 等价于把成员整体作优先层、平台层为空调用 {@link #resolveChain}——存量预设按
     * 「专属层在前、平台层在后」生成时，解析结果与平台化前旧链逐条一致。
     */
    public static List<ResolvedLink> resolvePresetChain(List<EntrySnapshot> members, String mode) {
        return resolveChain(members, List.of(), mode);
    }

    private static List<EntrySnapshot> filterMode(List<EntrySnapshot> entries, String mode) {
        if (entries == null) {
            return List.of();
        }
        if (mode == null) {
            return entries;
        }
        return entries.stream().filter(entry -> mode.equals(entryMode(entry.mode()))).toList();
    }

    private static EntrySnapshot firstDefault(List<EntrySnapshot> entries) {
        for (EntrySnapshot entry : entries) {
            if (entry.defaultEntry()) {
                return entry;
            }
        }
        return null;
    }

    private static void appendLinks(List<ResolvedLink> chain, List<EntrySnapshot> entries, Long skipId) {
        for (EntrySnapshot entry : entries) {
            if (entry.id() != null && entry.id().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = toSelection(entry);
            if (selection != null) {
                chain.add(linkOf(entry, selection));
            }
        }
    }

    private static ResolvedLink linkOf(EntrySnapshot entry, AgentModelSelection selection) {
        String name = StringUtils.hasText(entry.displayName()) ? entry.displayName().trim() : selection.spec();
        return new ResolvedLink(entry.id(), name, selection);
    }

    /** 凭证组装：无 spec 或无 key 返回 null（坏入口不得顶上链）；CLI 入口的 embedding spec 强制清空。 */
    private static AgentModelSelection toSelection(EntrySnapshot entry) {
        if (entry == null || !StringUtils.hasText(entry.modelSpec()) || !StringUtils.hasText(entry.apiKey())) {
            return null;
        }
        String baseUrl = StringUtils.hasText(entry.baseUrl()) ? entry.baseUrl().trim() : null;
        return new AgentModelSelection(
                entry.modelSpec().trim(), entry.apiKey(), baseUrl, embeddingSpecFor(entry));
    }

    private static String embeddingSpecFor(EntrySnapshot entry) {
        if ("CLI".equals(entryMode(entry.mode())) || !StringUtils.hasText(entry.embeddingSpec())) {
            return null;
        }
        return entry.embeddingSpec().trim();
    }
}
