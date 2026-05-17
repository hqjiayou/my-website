package com.mywebsite.infra.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MessageKeysTest {

    @Test
    void typicalMessageKeyPasses() {
        assertThat(MessageKeys.looksLikeMessageKey("admin.auth.login.failed")).isTrue();
        assertThat(MessageKeys.looksLikeMessageKey("common.error.bad-request")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "message1", "ADMIN", ".", "ただの自然文"})
    void nonSemanticOrNonKeyStringsRejected(String raw) {
        assertThat(MessageKeys.looksLikeMessageKey(raw)).isFalse();
    }
}
