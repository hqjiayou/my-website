package com.mywebsite.agent.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.agent.dto.AgentModelSelection;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEmbeddingClassUtilsTest {

    @Test
    void googleGlaCollapsesOntoGoogleAndCapKeepsTheFirstFour() {
        List<AgentModelSelection> chosen = AgentEmbeddingClassUtils.dedupe(List.of(
                selection("google-gla:gemini-embedding-001"),
                selection("google:gemini-embedding-001"),
                selection("openai:text-embedding-3-small"),
                selection("openai:text-embedding-3-large"),
                selection("openai:embed-extra"),
                selection("openai:embed-dropped")));

        assertThat(chosen).hasSize(4);
        assertThat(chosen.get(0).embeddingSpec()).isEqualTo("google-gla:gemini-embedding-001");
        assertThat(chosen).extracting(AgentModelSelection::embeddingSpec).doesNotContain("openai:embed-dropped");
    }

    @Test
    void storedSingleSpecMatchesTheNormalizedClass() {
        List<AgentModelSelection> current = List.of(selection("google:gemini-embedding-001"));

        assertThat(AgentEmbeddingClassUtils.matchesStored("google-gla:gemini-embedding-001", current))
                .isTrue();
        assertThat(AgentEmbeddingClassUtils.canonical(current)).isEqualTo("google:gemini-embedding-001");
    }

    @Test
    void aNewClassIsAProperSubsetAndOnlyThatClassIsMissing() {
        List<AgentModelSelection> current =
                List.of(selection("openai:embed-b"), selection("openai:embed-a"));

        assertThat(AgentEmbeddingClassUtils.isProperSubset("openai:embed-a", current)).isTrue();
        assertThat(AgentEmbeddingClassUtils.missing("openai:embed-a", current))
                .extracting(AgentModelSelection::embeddingSpec)
                .containsExactly("openai:embed-b");
        assertThat(AgentEmbeddingClassUtils.canonical(current)).isEqualTo("openai:embed-a\nopenai:embed-b");
        assertThat(AgentEmbeddingClassUtils.matchesStored("openai:embed-a\nopenai:embed-b", current))
                .isTrue();
    }

    private static AgentModelSelection selection(String embeddingSpec) {
        return new AgentModelSelection("openai:gpt", "key", null, embeddingSpec);
    }
}
