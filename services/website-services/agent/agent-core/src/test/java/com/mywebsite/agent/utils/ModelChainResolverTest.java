package com.mywebsite.agent.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.utils.ModelChainResolver.EntrySnapshot;
import com.mywebsite.agent.utils.ModelChainResolver.ResolvedLink;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 组链规则的唯一守卫（Issue #132）：admin 与 agent-app 两侧共用
 * {@link ModelChainResolver} 后，语义由这组纯单测钉死，不再靠两处实现互保。
 * 嵌入类已随 Issue #126 登记制迁出本类，守卫在 AgentEmbeddingClassServiceImplTest。
 */
class ModelChainResolverTest {

    private static EntrySnapshot entry(
            long id, String spec, String key, boolean isDefault, String mode, String embeddingSpec) {
        return new EntrySnapshot(id, "e" + id, spec, key, null, embeddingSpec, isDefault, mode);
    }

    private static EntrySnapshot keyEntry(long id, String spec, boolean isDefault) {
        return entry(id, spec, "key-" + id, isDefault, "KEY", null);
    }

    @Test
    void siteDefaultIsPrimaryThenSiteOthersThenGlobalOthers() {
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(
                List.of(keyEntry(2, "google:b", false), keyEntry(1, "google:a", true)),
                List.of(keyEntry(9, "openai:z", true), keyEntry(8, "openai:y", false)),
                null);

        // 输入顺序即组链顺序（调用方按默认在前、id 升序查好）：站点默认 1 为主用，
        // 其后站点其余 2，再全站其余（全站默认 9 不是主用时也只是普通备援）9、8。
        assertThat(chain).extracting(ResolvedLink::id).containsExactly(1L, 2L, 9L, 8L);
    }

    @Test
    void globalDefaultIsPrimaryWhenSiteHasNone() {
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(
                List.of(keyEntry(2, "google:b", false)),
                List.of(keyEntry(9, "openai:z", true)),
                null);

        assertThat(chain).extracting(ResolvedLink::id).containsExactly(9L, 2L);
    }

    @Test
    void noDefaultAnywhereMeansEmptyChain() {
        assertThat(ModelChainResolver.resolveChain(
                        List.of(keyEntry(2, "google:b", false)), List.of(keyEntry(9, "openai:z", false)), null))
                .isEmpty();
    }

    @Test
    void brokenPrimaryKeyPoisonsWholeChain() {
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(
                List.of(entry(1, "google:a", null, true, "KEY", null), keyEntry(2, "google:b", false)),
                List.of(),
                null);

        assertThat(chain).isEmpty();
    }

    @Test
    void brokenBackupIsSkippedSilently() {
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(
                List.of(keyEntry(1, "google:a", true), entry(2, "google:b", "", false, "KEY", null)),
                List.of(),
                null);

        assertThat(chain).extracting(ResolvedLink::id).containsExactly(1L);
    }

    @Test
    void modeFilterClosesBackupsInsideTheMode() {
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(
                List.of(
                        entry(1, "google:a", "k1", true, "KEY", null),
                        entry(2, "cli:b", "k2", false, "CLI", null),
                        entry(3, "google:c", "k3", false, null, null)),
                List.of(entry(9, "cli:z", "k9", true, "CLI", null)),
                "CLI");

        // KEY 默认被过滤掉后，CLI 的主用是全站 CLI 默认 9；站点 CLI 2 作备援；
        // mode 列为空按 KEY 处理（3 号）不进 CLI 链。
        assertThat(chain).extracting(ResolvedLink::id).containsExactly(9L, 2L);
    }

    @Test
    void cliEntryEmbeddingSpecIsClearedFromSelection() {
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(
                List.of(entry(1, "cli:a", "k1", true, "CLI", "google:embed-1")),
                List.of(),
                null);

        assertThat(chain).hasSize(1);
        assertThat(chain.get(0).selection().embeddingSpec()).isNull();
    }

    @Test
    void displayNameFallsBackToSpec() {
        EntrySnapshot unnamed = new EntrySnapshot(5L, "  ", "google:a", "k5", null, null, true, "KEY");
        List<ResolvedLink> chain = ModelChainResolver.resolveChain(List.of(unnamed), List.of(), null);

        assertThat(chain.get(0).displayName()).isEqualTo("google:a");
    }

    // ===== 成员链形态（Issue #210 UoW-7 起，#214 UoW-3 成员来源改 agent 自有链）：成员整体即链，顺序为成员序 =====

    @Test
    void presetChainUsesFirstDefaultInPresetOrderAsPrimary() {
        List<ResolvedLink> chain = ModelChainResolver.resolveMemberChain(
                List.of(
                        keyEntry(4, "openai:alt", false),
                        keyEntry(1, "google:default", true),
                        keyEntry(2, "anthropic:alt", false)),
                null);

        // 预设序内第一个默认作主用并提到链首，其余保持预设序备援
        assertThat(chain).extracting(ResolvedLink::id).containsExactly(1L, 4L, 2L);
    }

    @Test
    void presetChainFiltersModeAndClosesBackupsInsideIt() {
        List<ResolvedLink> chain = ModelChainResolver.resolveMemberChain(
                List.of(
                        keyEntry(3, "google:key-default", true),
                        entry(5, "openai:cli-default", "k5", true, "CLI", null),
                        entry(6, "anthropic:cli-alt", "k6", false, "CLI", null),
                        keyEntry(1, "google:key-alt", false)),
                "CLI");

        assertThat(chain).extracting(ResolvedLink::id).containsExactly(5L, 6L);
    }

    @Test
    void presetChainIsEmptyWithoutDefaultOrWithBrokenPrimary() {
        assertThat(ModelChainResolver.resolveMemberChain(
                        List.of(keyEntry(2, "google:b", false)), null))
                .isEmpty();
        assertThat(ModelChainResolver.resolveMemberChain(
                        List.of(
                                entry(1, "google:a", null, true, "KEY", null),
                                keyEntry(2, "google:b", false)),
                        null))
                .isEmpty();
        assertThat(ModelChainResolver.resolveMemberChain(List.of(), null)).isEmpty();
    }
}
