package com.mywebsite.agent.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.utils.ModelChainResolver.EntrySnapshot;
import com.mywebsite.agent.utils.ModelChainResolver.ResolvedLink;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 组链/嵌入类规则的唯一守卫（Issue #132）：admin 与 agent-app 两侧共用
 * {@link ModelChainResolver} 后，语义由这组纯单测钉死，不再靠两处实现互保。
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

    @Test
    void embeddingClassesTakeKeyEntriesWithSpecSiteFirstAndIgnoreChainState() {
        List<AgentModelSelection> classes = ModelChainResolver.resolveEmbeddingClasses(
                List.of(
                        entry(1, "google:a", "k1", false, "KEY", "google:embed-1"),
                        entry(2, "cli:b", "k2", false, "CLI", "google:embed-2"),
                        entry(3, "google:c", "k3", false, "KEY", null)),
                List.of(entry(9, "openai:z", "k9", false, "KEY", "openai:embed-3")));

        // 没有默认入口也照常算（与组链独立）；CLI 与无 spec 的不进；站点优先。
        assertThat(classes).extracting(AgentModelSelection::embeddingSpec)
                .containsExactly("google:embed-1", "openai:embed-3");
    }

    @Test
    void embeddingClassesSkipEntriesWhoseKeyIsMissing() {
        List<AgentModelSelection> classes = ModelChainResolver.resolveEmbeddingClasses(
                List.of(entry(1, "google:a", null, false, "KEY", "google:embed-1")), List.of());

        assertThat(classes).isEmpty();
    }
}
