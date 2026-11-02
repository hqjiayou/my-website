package com.mywebsite.agent.utils;

import com.mywebsite.agent.dto.AgentModelSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 模型入口组链与嵌入类选择的唯一实现（Issue #132）。此前 admin 的
 * {@code AdminAiModelEntryServiceImpl.selectionChain} 与 agent-app 的
 * {@code AgentAiServiceImpl.loadModelChain} 两处手写同规则、靠注释互保一致；
 * 现在规则只在这里，两侧把各自实体映射成 {@link EntrySnapshot} 后调用，
 * 实体与 Mapper 仍归各模块（同表双映射不在本单范围）。
 *
 * <p>纯静态、无状态：解密由调用方完成并把明文 key 放进快照（解不出放 null）。
 * 输入列表的顺序即组链顺序，调用方须按「默认在前、id 升序」查好再传入。
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
     * 生效链：先按 {@code mode} 过滤（null 表示整链、不过滤）；主用＝站点作用域
     * 第一个默认入口，否则全站第一个默认；主用不存在或其凭证组装不出（无 spec／
     * 无 key）时整链为空——备援不得越过坏主用顶上（ADR-0004）；其后依次是同站
     * 其余、全站其余（保持输入顺序），凭证组装不出的备援跳过。
     */
    public static List<ResolvedLink> resolveChain(
            List<EntrySnapshot> agentScope, List<EntrySnapshot> globalScope, String mode) {
        List<EntrySnapshot> agentEntries = filterMode(agentScope, mode);
        List<EntrySnapshot> globalEntries = filterMode(globalScope, mode);
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
     * 已登记的嵌入类：只取 KEY 模式、带 embedding spec 且凭证可组装的入口，
     * 站点在前、全站在后，最后经 {@link AgentEmbeddingClassUtils#dedupe} 按
     * 规范化 spec 去重并截到上限。与组链相互独立：没有默认入口也能算嵌入类。
     */
    public static List<AgentModelSelection> resolveEmbeddingClasses(
            List<EntrySnapshot> agentScope, List<EntrySnapshot> globalScope) {
        List<AgentModelSelection> raw = new ArrayList<>();
        appendEmbeddings(raw, agentScope);
        appendEmbeddings(raw, globalScope);
        return AgentEmbeddingClassUtils.dedupe(raw);
    }

    private static void appendEmbeddings(List<AgentModelSelection> raw, List<EntrySnapshot> entries) {
        if (entries == null) {
            return;
        }
        for (EntrySnapshot entry : entries) {
            if (!"KEY".equals(entryMode(entry.mode())) || !StringUtils.hasText(entry.embeddingSpec())) {
                continue;
            }
            AgentModelSelection selection = toSelection(entry);
            if (selection != null && StringUtils.hasText(selection.embeddingSpec())) {
                raw.add(selection);
            }
        }
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
